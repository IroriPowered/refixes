package cc.irori.refixes.service;

import cc.irori.refixes.compat.HyboxBridge;
import cc.irori.refixes.config.impl.WatchdogConfig;
import cc.irori.refixes.config.impl.WatchdogRecoveryConfig;
import cc.irori.refixes.early.accessor.TickingThreadAccess;
import cc.irori.refixes.util.Early;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.ShutdownReason;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

public class WatchdogService {

    private static final long WORLD_RESPONSE_ERROR = -1L;
    private static final int MAX_RESTART_FAILURES = 5;
    private static final long ALERT_REPEAT_MS = 60_000L;
    private static final HytaleLogger LOGGER = Logs.logger();

    private final AtomicLong defaultWorldResponse = new AtomicLong(System.currentTimeMillis());
    private final Map<String, Long> worldResponseMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> worldRestartFailures = new ConcurrentHashMap<>();
    private final Set<String> worldsGivenUp = ConcurrentHashMap.newKeySet();
    private final Set<String> intentionallyRemoved = ConcurrentHashMap.newKeySet();
    private final Set<String> restartCandidates = ConcurrentHashMap.newKeySet();
    private final Set<String> selfInitiatedRemovals = ConcurrentHashMap.newKeySet();
    private final Set<String> worldsRejectingTasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean armed = new AtomicBoolean();

    private final boolean canRestartWorlds =
            Early.hasMixins("MixinTickingThreadAccess") && TickingThreadAccess.class.isAssignableFrom(World.class);
    private volatile boolean stopped;
    private Thread watchdogThread;
    private World lastDefaultWorld;
    private long lastAlertAt;

    private volatile State state = State.ACTIVATING;

    public WatchdogService() {
        if (WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.AUTO_RESTART_WORLDS) && !canRestartWorlds) {
            LOGGER.atWarning().log(
                    "World auto-restart is inactive without its early mixin. Watchdog monitoring remains active.");
        }
    }

    public State getState() {
        return state;
    }

    public void registerService() {
        CompletableFuture<Void> universeReady = Universe.get().getUniverseReady();
        if (universeReady != null) {
            universeReady.whenComplete((ignored, failure) -> arm());
        } else {
            LOGGER.atInfo().log("Server watchdog will start once all worlds have loaded");
        }
    }

    public void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry().registerGlobal(EventPriority.LAST, RemoveWorldEvent.class, this::onRemoveWorld);
        plugin.getEventRegistry().registerGlobal(EventPriority.LAST, AddWorldEvent.class, this::onAddWorld);
        plugin.getEventRegistry().register(AllWorldsLoadedEvent.class, event -> arm());
    }

    private void arm() {
        if (stopped || !armed.compareAndSet(false, true)) {
            return;
        }
        for (World world : Universe.get().getWorlds().values()) {
            if (!world.getName().startsWith(InstancesPlugin.INSTANCE_PREFIX)) restartCandidates.add(world.getName());
        }
        lastDefaultWorld = Universe.get().getDefaultWorld();
        defaultWorldResponse.set(System.currentTimeMillis());
        start();
    }

    private void onRemoveWorld(RemoveWorldEvent event) {
        // EXCEPTIONAL = crash path; let the watchdog auto-restart those.
        if (event.getRemovalReason() == RemoveWorldEvent.RemovalReason.EXCEPTIONAL) {
            return;
        }
        if (event.isCancelled()) {
            return;
        }
        String name = event.getWorld().getName();
        if (selfInitiatedRemovals.remove(name)) {
            // The watchdog itself initiated this removal as part of an auto-restart.
            return;
        }
        worldResponseMap.remove(name);
        restartCandidates.remove(name);
        intentionallyRemoved.add(name);
        LOGGER.atInfo().log("World '%s' removed externally; watchdog will not auto-restart it", name);
    }

    private void onAddWorld(AddWorldEvent event) {
        if (event.isCancelled()) {
            return;
        }
        String name = event.getWorld().getName();
        if (!name.startsWith(InstancesPlugin.INSTANCE_PREFIX)) restartCandidates.add(name);
        intentionallyRemoved.remove(name);
        worldsRejectingTasks.remove(name);
    }

    public synchronized void unregisterService() {
        stopped = true;
        LOGGER.atInfo().log("Stopping server watchdog");
        if (watchdogThread != null
                && !(HytaleServer.get().isShuttingDown()
                        && WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.HALT_ON_SHUTDOWN_TIMEOUT))) {
            watchdogThread.interrupt();
        }
    }

    private synchronized void start() {
        if (stopped) return;
        String worldName = lastDefaultWorld != null ? lastDefaultWorld.getName() : "<not loaded>";
        LOGGER.atInfo().log("Starting server watchdog (default world: %s)", worldName);
        watchdogThread = new Thread(this::runWatchdog, "Refixes-Watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.start();
    }

    private void runWatchdog() {
        state = State.ACTIVATING;

        try {
            int delay = WatchdogConfig.get().getValue(WatchdogConfig.ACTIVATION_DELAY_MS);
            double seconds = (double) delay / 1000;
            LOGGER.atInfo().log("Watchdog will activate in %.2f seconds", seconds);
            Thread.sleep(delay);

            state = State.RUNNING;
            LOGGER.atInfo().log("Watchdog running");

            while (true) {
                if (watchForServerShutdown()) return;

                // Send request -> wait -> check response
                requestAutoRestartingWorldResponses();
                requestDefaultWorldResponse();
                Thread.sleep(5000);
                if (watchForServerShutdown()) return;
                watchForAutoRestartingWorlds();
                watchForDefaultWorld();
            }
        } catch (InterruptedException e) {
            LOGGER.atFine().withCause(e).log("Watchdog thread was interrupted");
        } catch (Throwable t) {
            LOGGER.atSevere().withCause(t).log("Watchdog encountered an error, restarting");
            start();
        }
    }

    private void requestAutoRestartingWorldResponses() {
        if (!canRestartWorlds || !WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.AUTO_RESTART_WORLDS)) {
            return;
        }

        List<String> worldNames = new ArrayList<>();
        String[] configWorldNames =
                WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.AUTO_RESTARTING_WORLD_FILTER);
        for (String worldName : configWorldNames) {
            if (!worldName.trim().isEmpty()) {
                worldNames.add(worldName.trim());
            }
        }

        if (worldNames.isEmpty()) {
            String defaultWorldName =
                    HytaleServer.get().getConfig().getDefaults().getWorld();
            boolean shutdownOnDefaultCrash =
                    WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH);

            // No custom filter set, check status for all loaded worlds
            for (World world : Universe.get().getWorlds().values()) {
                String worldName = world.getName();
                if (shutdownOnDefaultCrash && worldName.equals(defaultWorldName)) {
                    continue;
                }

                if (!worldName.startsWith(InstancesPlugin.INSTANCE_PREFIX)) {
                    worldNames.add(worldName);
                }
            }
        }

        for (String worldName : worldNames) {
            World world = Universe.get().getWorld(worldName);
            if (world == null) {
                continue;
            }
            if (!world.isStarted()) {
                worldResponseMap.remove(worldName);
                continue;
            }

            worldResponseMap.putIfAbsent(worldName, System.currentTimeMillis());
            try {
                // Wait for response on world thread
                world.execute(
                        () -> worldResponseMap.computeIfPresent(worldName, (key, last) -> System.currentTimeMillis()));
                worldsRejectingTasks.remove(worldName);
            } catch (Exception e) {
                worldResponseMap.put(worldName, WORLD_RESPONSE_ERROR);
                if (worldsRejectingTasks.add(worldName)) {
                    LOGGER.atWarning().withCause(e).log("World %s did not accept a watchdog task", worldName);
                }
            }
        }
    }

    private void watchForAutoRestartingWorlds() {
        if (!canRestartWorlds || !WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.AUTO_RESTART_WORLDS)) {
            return;
        }

        if (HytaleServer.get().isShuttingDown()) {
            return;
        }

        List<String> worldNames = new ArrayList<>();
        String[] configWorldNames =
                WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.AUTO_RESTARTING_WORLD_FILTER);
        for (String worldName : configWorldNames) {
            if (!worldName.trim().isEmpty()) {
                worldNames.add(worldName.trim());
            }
        }

        if (worldNames.isEmpty()) {
            String defaultWorldName =
                    HytaleServer.get().getConfig().getDefaults().getWorld();
            boolean shutdownOnDefaultCrash =
                    WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH);

            // No custom filter set, check worlds loaded since the watchdog armed
            for (String name : restartCandidates) {
                if (shutdownOnDefaultCrash && name.equals(defaultWorldName)) {
                    continue;
                }
                worldNames.add(name);
            }
        }

        for (String worldName : worldNames) {
            World world = Universe.get().getWorld(worldName);
            Long response = worldResponseMap.get(worldName);

            boolean restart = false;
            if (world == null || !world.isAlive()) {
                restart = true;
            } else if (response != null && response == WORLD_RESPONSE_ERROR) {
                LOGGER.atSevere().log(
                        "World %s was unable to accept tasks. The world may have been crashed.", worldName);
                restart = true;
            } else if (response != null) {
                long elapsed = System.currentTimeMillis() - response;
                if (elapsed > WatchdogConfig.get().getValue(WatchdogConfig.THREAD_TIMEOUT_MS)) {
                    LOGGER.atSevere().log(
                            "World %s did not respond for %.2f seconds. Auto-restart only restarts crashed worlds, not hung ones.",
                            worldName, elapsed / 1000.0);
                    dumpThreads(worldName);
                    worldResponseMap.computeIfPresent(worldName, (key, last) -> System.currentTimeMillis());
                }
            }

            if (restart) {
                worldResponseMap.remove(worldName);
                if (!Universe.get().isWorldLoadable(worldName)) {
                    continue;
                }
                if (worldsGivenUp.contains(worldName)) {
                    continue;
                }
                if (intentionallyRemoved.contains(worldName)) {
                    continue;
                }

                LOGGER.atSevere().log("========== AUTO WORLD RESTART ==========");
                LOGGER.atSevere().log("World: %s", worldName);
                HyboxBridge.event("Watchdog", "auto-restarting world '" + worldName + "'");
                dumpThreads(worldName);

                World worldToRestart = Universe.get().getWorld(worldName);
                if (worldToRestart != null) {
                    int saveTimeout =
                            WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.RESTART_SAVE_TIMEOUT_MS);
                    if (!trySaveWorldWithTimeout(worldToRestart, saveTimeout)) {
                        LOGGER.atSevere().log(
                                "Aborting auto-restart of '%s': save or shutdown did not safely complete within %dms",
                                worldName, saveTimeout);
                        HyboxBridge.event(
                                "Watchdog", "gave up on '" + worldName + "': save/shutdown not safely completed");
                        worldsGivenUp.add(worldName);
                        continue;
                    }
                }

                LOGGER.atInfo().log("Attempting to unload world: " + worldName);
                if (Universe.get().getWorld(worldName) != null) {
                    selfInitiatedRemovals.add(worldName);
                    try {
                        Universe.get().removeWorld(worldName);
                    } catch (Exception e) {
                        LOGGER.atWarning().withCause(e).log("Exception on unloading world %s", worldName);
                    } finally {
                        // Defensive: cleared by the event listener on success; this covers the case
                        // where the listener never ran (e.g. removeWorld threw before dispatching).
                        selfInitiatedRemovals.remove(worldName);
                    }
                }

                LOGGER.atInfo().log("Restarting world: %s", worldName);
                try {
                    Universe.get().loadWorld(worldName).join();
                    LOGGER.atInfo().log("World %s loaded", worldName);
                    HyboxBridge.event("Watchdog", "world '" + worldName + "' restarted");
                    worldRestartFailures.remove(worldName);
                } catch (Exception e) {
                    int failures = worldRestartFailures.merge(worldName, 1, Integer::sum);
                    LOGGER.atSevere().withCause(e).log(
                            "Failed to load world: %s (attempt %d/%d)", worldName, failures, MAX_RESTART_FAILURES);
                    if (failures >= MAX_RESTART_FAILURES) {
                        worldsGivenUp.add(worldName);
                        LOGGER.atSevere().log(
                                "Giving up on auto-restarting world '%s' after %d failures. Resolve the underlying issue and restart the server.",
                                worldName, failures);
                        HyboxBridge.event("Watchdog", "gave up on '" + worldName + "' after " + failures + " failures");
                    }
                }
            }
        }
    }

    private boolean trySaveWorldWithTimeout(World world, int timeoutMs) {
        try {
            if (!world.isAlive() || !world.isStarted()) {
                if (!(world instanceof TickingThreadAccess access)) {
                    LOGGER.atSevere().log(
                            "Cannot establish shutdown completion for world '%s': missing thread accessor",
                            world.getName());
                    return false;
                }
                Thread thread = access.refixes$getThread();
                if (thread == Thread.currentThread()) return false;
                if (thread != null) {
                    thread.join(Math.max(1L, timeoutMs));
                    if (thread.isAlive()) return false;
                }
                Store<?> chunkStore = world.getChunkStore().getStore();
                Store<?> entityStore = world.getEntityStore().getStore();
                if (chunkStore.isAliveInDifferentThread() || entityStore.isAliveInDifferentThread()) return false;
                boolean closed = chunkStore.isShutdown()
                        && entityStore.isShutdown()
                        && world.getChunkStore().getSaver() == null;
                if (!closed) {
                    LOGGER.atSevere().log(
                            "World '%s' terminated without completing store shutdown; refusing unsafe direct save",
                            world.getName());
                }
                return closed;
            }
            CompletableFuture<Void> save = CompletableFuture.supplyAsync(
                            () -> {
                                if (!world.isAlive()
                                        || !world.isStarted()
                                        || world.getChunkStore().getStore().isShutdown()
                                        || world.getEntityStore().getStore().isShutdown()
                                        || HytaleServer.get().isShuttingDown()) {
                                    throw new IllegalStateException("World began stopping before the watchdog save");
                                }
                                return world.getWorldConfig().canSaveChunks()
                                        ? ChunkSavingSystems.saveChunksInWorld(
                                                world.getChunkStore().getStore(), world)
                                        : CompletableFuture.<Void>completedFuture(null);
                            },
                            world)
                    .thenCompose(future -> future);
            save.get(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Error saving world '%s' before auto-restart", world.getName());
            return false;
        }
    }

    private void requestDefaultWorldResponse() throws InterruptedException {
        if (!WatchdogConfig.get().getValue(WatchdogConfig.ENABLED)
                && !WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH)) {
            return;
        }

        boolean shutdown = false;
        String shutdownReason = "Unknown";
        Throwable cause = null;

        World world = Universe.get().getDefaultWorld();
        if (world == null && Options.isBare()) {
            return;
        }
        if (world == null || !world.isAlive()) {
            shutdown = true;
            shutdownReason = "Default world " + (world != null ? world.getName() + " " : "") + "is not alive.";
        } else if (lastDefaultWorld != world) {
            LOGGER.atInfo().log("Default world changed to %s (%d)", world.getName(), world.hashCode());
            defaultWorldResponse.set(System.currentTimeMillis());
            lastDefaultWorld = world;
        }

        if (shutdown) {
            alert(shutdownReason);
            return;
        }

        if (!world.isStarted()) {
            defaultWorldResponse.set(System.currentTimeMillis());
            return;
        }

        try {
            world.execute(() -> {
                defaultWorldResponse.set(System.currentTimeMillis());
            });
        } catch (Exception e) {
            shutdown = true;
            shutdownReason =
                    "World " + world.getName() + " was unable to accept tasks. The world may have been crashed.";
            cause = e;
        }

        if (shutdown) {
            alert(shutdownReason, cause);
        }
    }

    private void watchForDefaultWorld() throws InterruptedException {
        if (!WatchdogConfig.get().getValue(WatchdogConfig.ENABLED)
                && !WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH)) {
            return;
        }

        if (lastDefaultWorld == null) {
            return;
        }

        long elapsed = System.currentTimeMillis() - defaultWorldResponse.get();
        if (elapsed > WatchdogConfig.get().getValue(WatchdogConfig.THREAD_TIMEOUT_MS)) {
            alert("World " + lastDefaultWorld.getName() + " did not respond for " + (elapsed / 1000) + " seconds.");
        }
    }

    private void alert(String reason) throws InterruptedException {
        alert(reason, null);
    }

    private void alert(String reason, @Nullable Throwable cause) throws InterruptedException {
        long now = System.currentTimeMillis();
        boolean repeat = lastAlertAt != 0 && defaultWorldResponse.get() < lastAlertAt;
        if (repeat
                && now - lastAlertAt < ALERT_REPEAT_MS
                && !WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH)) {
            return;
        }
        lastAlertAt = now;
        triggerWatchdog(reason, cause);
    }

    private static boolean watchForServerShutdown() throws InterruptedException {
        if (HytaleServer.get().isShuttingDown()) {
            LOGGER.atInfo().log("Server shutdown detected");
            handleShutdownTimeout();
            return true;
        }
        return false;
    }

    private static void triggerWatchdog(String reason, @Nullable Throwable cause) throws InterruptedException {
        LOGGER.atSevere().log("========== WATCHDOG ALERT ==========");
        LOGGER.atSevere().withCause(cause).log("Reason: %s", reason);
        HyboxBridge.event("Watchdog", reason);
        if (WatchdogConfig.get().getValue(WatchdogConfig.ENABLED)) dumpThreads();
        if (!WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH)) return;
        LOGGER.atSevere().log("Shutting down the server after watchdog alert");

        Thread.sleep(5000);
        HytaleServer.get()
                .shutdownServer(ShutdownReason.CRASH.withMessage(Message.raw("Watchdog triggered a shutdown")));
        handleShutdownTimeout();
    }

    private static void dumpThreads() {
        dumpThreads(null);
    }

    private static void dumpThreads(@Nullable String worldName) {
        boolean dumpAllThreads = WatchdogConfig.get().getValue(WatchdogConfig.DUMP_ALL_THREADS);

        Thread.getAllStackTraces().forEach((thread, stackTrace) -> {
            if (dumpAllThreads
                    || (worldName == null && thread.getName().startsWith("WorldThread"))
                    || (worldName != null && thread.getName().equals("WorldThread - " + worldName))) {
                LOGGER.atSevere().log("Thread: %s (ID: %d):", thread.getName(), thread.getId());
                for (StackTraceElement element : stackTrace) {
                    LOGGER.atSevere().log("    at %s", element.toString());
                }
            }
        });
    }

    private static void handleShutdownTimeout() throws InterruptedException {
        WatchdogRecoveryConfig config = WatchdogRecoveryConfig.get();
        if (!config.getValue(WatchdogRecoveryConfig.HALT_ON_SHUTDOWN_TIMEOUT)) {
            return;
        }
        Thread.sleep(config.getValue(WatchdogRecoveryConfig.SHUTDOWN_TIMEOUT_MS));
        LOGGER.atSevere().log("Shutdown cannot proceed. Forcing exit.");
        Runtime.getRuntime().halt(1);
    }

    public enum State {
        ACTIVATING,
        RUNNING
    }
}
