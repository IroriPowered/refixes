package cc.irori.refixes.service;

import cc.irori.refixes.compat.BlackboxBridge;
import cc.irori.refixes.config.impl.WatchdogConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ShutdownReason;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AddWorldEvent;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class WatchdogService {

    private static final long WORLD_RESPONSE_ERROR = -1L;
    private static final int MAX_RESTART_FAILURES = 5;
    private static final HytaleLogger LOGGER = Logs.logger();

    private final AtomicLong defaultWorldResponse = new AtomicLong(System.currentTimeMillis());
    private final Map<String, Long> worldResponseMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> worldRestartFailures = new ConcurrentHashMap<>();
    private final Set<String> worldsGivenUp = ConcurrentHashMap.newKeySet();
    private final Set<String> intentionallyRemoved = ConcurrentHashMap.newKeySet();
    private final Set<String> selfInitiatedRemovals = ConcurrentHashMap.newKeySet();

    private Thread watchdogThread;
    private World lastDefaultWorld;

    private volatile State state = State.ACTIVATING;

    public WatchdogService() {}

    public State getState() {
        return state;
    }

    public void registerService() {
        lastDefaultWorld = Universe.get().getDefaultWorld();
        start();
    }

    public void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onRemoveWorld);
        plugin.getEventRegistry().registerGlobal(AddWorldEvent.class, this::onAddWorld);
    }

    private void onRemoveWorld(RemoveWorldEvent event) {
        // EXCEPTIONAL = crash path; let the watchdog auto-restart those.
        if (event.getRemovalReason() == RemoveWorldEvent.RemovalReason.EXCEPTIONAL) {
            return;
        }
        String name = event.getWorld().getName();
        if (selfInitiatedRemovals.remove(name)) {
            // The watchdog itself initiated this removal as part of an auto-restart.
            return;
        }
        worldResponseMap.remove(name);
        intentionallyRemoved.add(name);
        LOGGER.atInfo().log("World '%s' removed externally; watchdog will not auto-restart it", name);
    }

    private void onAddWorld(AddWorldEvent event) {
        intentionallyRemoved.remove(event.getWorld().getName());
    }

    public void unregisterService() {
        LOGGER.atInfo().log("Stopping server watchdog");
        if (watchdogThread != null) {
            watchdogThread.interrupt();
        }
    }

    private void start() {
        String worldName = lastDefaultWorld != null ? lastDefaultWorld.getName() : "<not loaded>";
        LOGGER.atInfo().log("Starting server watchdog (default world: %s)", worldName);
        watchdogThread = new Thread(this::runWatchdog, "Refixes-Watchdog");
        watchdogThread.setDaemon(true);
        watchdogThread.start();
    }

    private void runWatchdog() {
        WatchdogConfig config = WatchdogConfig.get();
        state = State.ACTIVATING;

        try {
            int delay = config.getValue(WatchdogConfig.ACTIVATION_DELAY_MS);
            double seconds = (double) delay / 1000;
            LOGGER.atInfo().log("Watchdog will activate in %.2f seconds", seconds);

            Thread.sleep(delay);

            state = State.RUNNING;
            LOGGER.atInfo().log("Watchdog running");

            while (true) {
                watchForServerShutdown();

                // Send request -> wait -> check response
                requestAutoRestartingWorldResponses();
                requestDefaultWorldResponse();
                Thread.sleep(5000);
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
        WatchdogConfig config = WatchdogConfig.get();
        if (!config.getValue(WatchdogConfig.AUTO_RESTART_WORLDS)) {
            return;
        }

        List<String> worldNames = new ArrayList<>();
        String[] configWorldNames = config.getValue(WatchdogConfig.AUTO_RESTARTING_WORLD_FILTER);
        for (String worldName : configWorldNames) {
            if (!worldName.trim().isEmpty()) {
                worldNames.add(worldName.trim());
            }
        }

        if (worldNames.isEmpty()) {
            String defaultWorldName =
                    HytaleServer.get().getConfig().getDefaults().getWorld();
            boolean shutdownOnDefaultCrash = config.getValue(WatchdogConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH);

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

            worldResponseMap.computeIfAbsent(worldName, key -> WORLD_RESPONSE_ERROR);
            try {
                // Wait for response on world thread
                world.execute(() -> worldResponseMap.put(worldName, System.currentTimeMillis()));
            } catch (Exception ignored) {
            }
        }
    }

    private void watchForAutoRestartingWorlds() {
        WatchdogConfig config = WatchdogConfig.get();
        if (!config.getValue(WatchdogConfig.AUTO_RESTART_WORLDS)) {
            return;
        }

        if (HytaleServer.get().isShuttingDown()) {
            return;
        }

        List<String> worldNames = new ArrayList<>();
        String[] configWorldNames = config.getValue(WatchdogConfig.AUTO_RESTARTING_WORLD_FILTER);
        for (String worldName : configWorldNames) {
            if (!worldName.trim().isEmpty()) {
                worldNames.add(worldName.trim());
            }
        }

        if (worldNames.isEmpty()) {
            String defaultWorldName =
                    HytaleServer.get().getConfig().getDefaults().getWorld();
            boolean shutdownOnDefaultCrash = config.getValue(WatchdogConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH);

            // No custom filter set, scan loadable world names from disk
            Path worldsDir = Universe.get().getWorldsPath();
            try (Stream<Path> paths = Files.list(worldsDir)) {
                paths.filter(path -> {
                            if (!Files.isDirectory(path)) {
                                return false;
                            }
                            String name = path.getFileName().toString();
                            if (shutdownOnDefaultCrash && name.equals(defaultWorldName)) {
                                return false;
                            }
                            return !name.startsWith(InstancesPlugin.INSTANCE_PREFIX)
                                    && Universe.get().isWorldLoadable(name);
                        })
                        .forEach(path -> worldNames.add(path.getFileName().toString()));
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to list worlds directory");
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
                if (elapsed > config.getValue(WatchdogConfig.THREAD_TIMEOUT_MS)) {
                    LOGGER.atSevere().log("World %s did not respond for %.2f seconds.", worldName, elapsed / 1000.0);
                    restart = true;
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
                BlackboxBridge.event("Watchdog", "auto-restarting world '" + worldName + "'");
                dumpThreads(worldName);

                World worldToRestart = Universe.get().getWorld(worldName);
                if (worldToRestart != null && worldToRestart.getWorldConfig().canSaveChunks()) {
                    int saveTimeout = config.getValue(WatchdogConfig.RESTART_SAVE_TIMEOUT_MS);
                    if (!trySaveWorldWithTimeout(worldToRestart, saveTimeout)) {
                        LOGGER.atSevere().log(
                                "Aborting auto-restart of '%s': save timed out (%dms)", worldName, saveTimeout);
                        BlackboxBridge.event(
                                "Watchdog", "gave up on '" + worldName + "': save timed out (" + saveTimeout + "ms)");
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
                    BlackboxBridge.event("Watchdog", "world '" + worldName + "' restarted");
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
                        BlackboxBridge.event(
                                "Watchdog", "gave up on '" + worldName + "' after " + failures + " failures");
                    }
                }
            }
        }
    }

    private boolean trySaveWorldWithTimeout(World world, int timeoutMs) {
        try {
            ChunkSavingSystems.saveChunksInWorld(world.getChunkStore().getStore())
                    .get(timeoutMs, TimeUnit.MILLISECONDS);
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
        WatchdogConfig config = WatchdogConfig.get();
        if (!config.getValue(WatchdogConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH)) {
            return;
        }

        boolean shutdown = false;
        String shutdownReason = "Unknown";

        World world = Universe.get().getDefaultWorld();
        if (world == null || !world.isAlive()) {
            shutdown = true;
            shutdownReason = "Default world " + (world != null ? world.getName() + " " : "") + "is not alive.";
        } else if (lastDefaultWorld != world) {
            LOGGER.atInfo().log("Default world changed to %s (%d)", world.getName(), world.hashCode());
            defaultWorldResponse.set(System.currentTimeMillis());
            lastDefaultWorld = world;
        }

        checkAndShutdown(shutdownReason, shutdown);

        try {
            world.execute(() -> {
                defaultWorldResponse.set(System.currentTimeMillis());
            });
        } catch (Exception e) {
            shutdown = true;
            shutdownReason =
                    "World " + world.getName() + " was unable to accept tasks. The world may have been crashed.";
        }

        checkAndShutdown(shutdownReason, shutdown);
    }

    private void watchForDefaultWorld() throws InterruptedException {
        WatchdogConfig config = WatchdogConfig.get();
        if (!config.getValue(WatchdogConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH)) {
            return;
        }

        if (lastDefaultWorld == null) {
            return;
        }

        long elapsed = System.currentTimeMillis() - defaultWorldResponse.get();
        if (elapsed > config.getValue(WatchdogConfig.THREAD_TIMEOUT_MS)) {
            triggerWatchdog(
                    "World " + lastDefaultWorld.getName() + " did not respond for " + (elapsed / 1000) + " seconds.");
        }
    }

    private static void watchForServerShutdown() throws InterruptedException {
        if (HytaleServer.get().isShuttingDown()) {
            LOGGER.atInfo().log("Server shutdown detected");
            handleShutdownTimeout();
        }
    }

    private static void checkAndShutdown(String reason, boolean shutdown) throws InterruptedException {
        if (shutdown) {
            triggerWatchdog(reason);
        }
    }

    private static void triggerWatchdog(String reason) throws InterruptedException {
        LOGGER.atSevere().log("========== AUTO SERVER SHUTDOWN ==========");
        LOGGER.atSevere().log("Reason: %s", reason);
        LOGGER.atSevere().log("Dumping threads and shutting down the server...");
        BlackboxBridge.event("Watchdog", "server shutdown: " + reason);

        dumpThreads();

        Thread.sleep(5000);
        HytaleServer.get()
                .shutdownServer(ShutdownReason.CRASH.withMessage(Message.raw("Watchdog triggered a shutdown")));
        handleShutdownTimeout();
    }

    private static void dumpThreads() {
        dumpThreads(null);
    }

    private static void dumpThreads(@Nullable String worldName) {
        WatchdogConfig config = WatchdogConfig.get();
        boolean dumpAllThreads = config.getValue(WatchdogConfig.DUMP_ALL_THREADS);

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
        WatchdogConfig config = WatchdogConfig.get();

        Thread.sleep(config.getValue(WatchdogConfig.SHUTDOWN_TIMEOUT_MS));
        LOGGER.atSevere().log("Shutdown cannot proceed. Forcing exit.");
        Runtime.getRuntime().halt(1);
    }

    public enum State {
        ACTIVATING,
        RUNNING
    }
}
