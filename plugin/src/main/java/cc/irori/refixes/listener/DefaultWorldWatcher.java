package cc.irori.refixes.listener;

import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DefaultWorldWatcher {

    private static final HytaleLogger LOGGER = Logs.logger();

    private final AtomicBoolean recoveryInProgress = new AtomicBoolean(false);
    private final AtomicInteger recoveryCount = new AtomicInteger(0);
    private volatile long lastRecoveryAttempt = 0;

    private static final long RECOVERY_DELAY_MS = 2000;
    private static final long RECOVERY_COOLDOWN_MS = 30000;
    private static final int MAX_RECOVERY_ATTEMPTS = 5;

    public void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry().registerGlobal(RemoveWorldEvent.class, this::onRemoveWorld);
        LOGGER.atInfo().log("Default world recovery is enabled");
    }

    private void onRemoveWorld(RemoveWorldEvent event) {
        World world = event.getWorld();
        // noinspection ConstantConditions
        if (world == null) {
            return;
        }

        String worldName = world.getName();
        RemoveWorldEvent.RemovalReason reason = event.getRemovalReason();

        if (reason != RemoveWorldEvent.RemovalReason.EXCEPTIONAL) {
            return;
        }

        String defaultWorldName = getDefaultWorldName();
        if (defaultWorldName == null || !defaultWorldName.equalsIgnoreCase(worldName)) {
            return;
        }

        LOGGER.atWarning().log("Default world '%s' needs recovery!", worldName);
        scheduleRecovery(worldName);
    }

    public void scheduleRecovery(String worldName) {
        if (!recoveryInProgress.compareAndSet(false, true)) {
            LOGGER.atInfo().log("Recovery is already in progress and cannot be re-scheduled");
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastRecoveryAttempt < RECOVERY_COOLDOWN_MS) {
            long waitTime = RECOVERY_COOLDOWN_MS - (now - lastRecoveryAttempt);
            LOGGER.atInfo().log("Waiting %d ms before default world recovery", waitTime);
            recoveryInProgress.set(false);

            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> scheduleRecovery(worldName), waitTime, TimeUnit.SECONDS);
            return;
        }

        int attempts = recoveryCount.incrementAndGet();
        if (attempts > MAX_RECOVERY_ATTEMPTS) {
            LOGGER.atSevere().log("Cannot recover default world after %d attempts", MAX_RECOVERY_ATTEMPTS);
            recoveryInProgress.set(false);
            return;
        }

        lastRecoveryAttempt = now;
        LOGGER.atInfo().log("Default world recovery in %d ms (attempt %d)", RECOVERY_DELAY_MS, attempts);

        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> performRecovery(worldName), RECOVERY_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void performRecovery(String worldName) {
        try {
            Universe universe = Universe.get();

            World existingWorld = universe.getWorld(worldName);
            if (existingWorld != null) {
                LOGGER.atInfo().log("Skipping recovery because world '%s' already exists", worldName);
                recoveryInProgress.set(false);
                return;
            }

            if (!universe.isWorldLoadable(worldName)) {
                LOGGER.atSevere().log("Cannot recover default world '%s' because it is not loadable", worldName);
                recoveryInProgress.set(false);
                return;
            }

            LOGGER.atInfo().log("Loading world '%s' for recovery", worldName);

            CompletableFuture<World> loading = universe.loadWorld(worldName);
            loading.whenComplete((world, throwable) -> {
                recoveryInProgress.set(false);

                if (throwable != null) {
                    LOGGER.atSevere().withCause(throwable).log("Failed to recover world '%s'", worldName);
                    return;
                }

                if (world == null) {
                    LOGGER.atSevere().log("Returned null for world load '%s", worldName);
                    return;
                }

                recoveryCount.set(0);
                LOGGER.atInfo().log("Successfully recovered world '%s'", worldName);
            });
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to start recovery for world '%s'", worldName);
            recoveryInProgress.set(false);
        }
    }

    private static String getDefaultWorldName() {
        try {
            return HytaleServer.get().getConfig().getDefaults().getWorld();
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to get default world name");
        }
        return "default";
    }
}
