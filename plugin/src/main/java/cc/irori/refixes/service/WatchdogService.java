package cc.irori.refixes.service;

import cc.irori.refixes.config.impl.WatchdogConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.ShutdownReason;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import javax.annotation.Nullable;

public class WatchdogService {

    private static final long WORLD_RESPONSE_ERROR = -1L;
    private static final HytaleLogger LOGGER = Logs.logger();

    private final AtomicLong defaultWorldResponse = new AtomicLong(System.currentTimeMillis());
    private final Map<String, Long> worldResponseMap = new ConcurrentHashMap<>();

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
            for (String worldName : Universe.get().getWorlds().keySet()) {
                // Shutdown on default crash will be handled in a different method
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

                LOGGER.atSevere().log("========== AUTO WORLD RESTART ==========");
                LOGGER.atSevere().log("World: %s", worldName);
                dumpThreads(worldName);

                LOGGER.atInfo().log("Attempting to unload world: " + worldName);
                try {
                    Universe.get().removeWorld(worldName);
                } catch (NullPointerException e) {
                    LOGGER.atWarning().withCause(e).log("Exception on unloading world %s", worldName);
                }

                LOGGER.atInfo().log("Restarting world: %s", worldName);
                try {
                    Universe.get().loadWorld(worldName).join();
                    LOGGER.atInfo().log("World %s loaded", worldName);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load world: %s", worldName);
                }
            }
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

        dumpThreads();

        Thread.sleep(5000);
        HytaleServer.get().shutdownServer(ShutdownReason.CRASH.withMessage("Watchdog triggered a shutdown"));
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
