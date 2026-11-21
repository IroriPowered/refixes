package cc.irori.refixes.service;

import cc.irori.refixes.config.impl.PerPlayerHotRadiusConfig;
import cc.irori.refixes.early.accessor.ServerViewRadiusAccess;
import cc.irori.refixes.jfr.RefixesEvents;
import cc.irori.refixes.util.Early;
import cc.irori.refixes.util.HeapPressureMonitor;
import cc.irori.refixes.util.Logs;
import cc.irori.refixes.util.PlayerRadiusRevisions;
import cc.irori.refixes.util.TpsUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.HytaleServerConfig;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PerPlayerHotRadiusService {

    private static final HytaleLogger LOGGER = Logs.logger();

    private static final int MEMORY_VIEW_INCREASE_STEP = 1;
    private static final int RADIUS_INCREASE_STEP = 1;
    private static final Path VIEW_RADIUS_RESTORE_FILE =
            Paths.get("mods", "IroriPowered_Refixes", "viewradius-restore");

    private ScheduledFuture<?> task;
    private boolean registered;
    private final Map<ChunkTracker, ManagedRadius> managedRadii = new HashMap<>();
    private final Map<World, Integer> worldTargets = new HashMap<>();
    private volatile int currentTargetRadius;
    private volatile IdlePlayerService idlePlayerService;
    private AutoCloseable radiusGauge;

    private final Object memoryLock = new Object();
    private HeapPressureMonitor heapMonitor;
    private int initialMaxViewRadius = -1;
    private int appliedMaxViewRadius = -1;
    private long viewRadiusRevision = -1;
    private long lastViewAdjustmentMs = 0L;

    public PerPlayerHotRadiusService() {
        currentTargetRadius = PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.MAX_RADIUS);
    }

    public void setIdlePlayerService(IdlePlayerService idlePlayerService) {
        this.idlePlayerService = idlePlayerService;
    }

    public static void restoreSavedViewRadius() {
        Integer radius = readRestore();
        if (radius != null) {
            restoreViewRadius(radius);
        }
    }

    private static void restoreViewRadius(int radius) {
        try {
            HytaleServerConfig config = HytaleServer.get().getConfig();
            if (config.getMaxViewRadius() != radius) {
                config.setMaxViewRadius(radius);
            }
            saveRestoredViewRadius(config);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to restore max view radius to %d", radius);
        }
    }

    private static void saveRestoredViewRadius(HytaleServerConfig config) {
        if (HytaleServer.get().isShuttingDown()) {
            return;
        }
        HytaleServerConfig.save(config).join();
        deleteRestore();
    }

    public synchronized void registerService() {
        if (registered) {
            return;
        }
        if (task != null) {
            task.cancel(false);
        }
        registered = true;
        if (PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.MEMORY_GUARD_ENABLED)
                && Early.hasMixins("MixinServerViewRadius")) {
            try {
                initMemoryGuard();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to initialize memory view-radius guard");
            }
        } else if (PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.MEMORY_GUARD_ENABLED)) {
            LOGGER.atWarning().log(
                    "Memory view-radius guard is inactive without Mixins.Compatibility.ServerViewRadius");
        }

        int interval =
                Math.max(1000, PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.CHECK_INTERVAL_MS));
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        checkAndAdjust();
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error while adjusting per-player hot radius");
                    }
                    try {
                        if (initialMaxViewRadius >= 0
                                && PerPlayerHotRadiusConfig.get()
                                        .getValue(PerPlayerHotRadiusConfig.MEMORY_GUARD_ENABLED)) {
                            recoverViewRadius();
                        }
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error while recovering view radius after memory pressure");
                    }
                },
                5000,
                interval,
                TimeUnit.MILLISECONDS);
        radiusGauge = RefixesEvents.gauge("PerPlayerHotRadius radius", () -> getCurrentTargetRadius());
    }

    public synchronized void unregisterService() {
        registered = false;
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (radiusGauge != null) {
            try {
                radiusGauge.close();
            } catch (Exception ignored) {
            }
            radiusGauge = null;
        }
        worldTargets.clear();
        restoreMemoryGuard();
        restoreHotRadii();
        if (!managedRadii.isEmpty() && !HytaleServer.get().isShuttingDown()) {
            task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(this::restoreHotRadii, 1, 1, TimeUnit.SECONDS);
        }
    }

    private synchronized void restoreHotRadii() {
        if (registered) {
            return;
        }
        Set<ChunkTracker> onlineTrackers = new HashSet<>();
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            ChunkTracker tracker = playerRef.getChunkTracker();
            onlineTrackers.add(tracker);
            ManagedRadius managed = managedRadii.get(tracker);
            if (managed == null) {
                continue;
            }
            if (managed.applied == managed.original) {
                managedRadii.remove(tracker, managed);
                continue;
            }
            var reference = playerRef.getReference();
            if (reference == null || !reference.isValid() || managed.pendingRestore == reference) {
                continue;
            }
            World world = reference.getStore().getExternalData().getWorld();
            managed.pendingRestore = reference;
            try {
                world.execute(() -> {
                    synchronized (PerPlayerHotRadiusService.this) {
                        if (managed.pendingRestore != reference) {
                            return;
                        }
                        managed.pendingRestore = null;
                        if (registered || managedRadii.get(tracker) != managed) {
                            return;
                        }
                        if (playerRef.getReference() != reference || !reference.isValid()) {
                            return;
                        }
                        IdlePlayerService idleService = idlePlayerService;
                        if (idleService != null) {
                            idleService.restoreHotRadius(
                                    playerRef, managed.original, managed.applied, managed.revision);
                        } else {
                            synchronized (tracker) {
                                if (tracker.getMaxHotLoadedRadius() == managed.applied
                                        && PlayerRadiusRevisions.hot(tracker) == managed.revision) {
                                    tracker.setMaxHotLoadedRadius(managed.original);
                                }
                            }
                        }
                        managedRadii.remove(tracker, managed);
                    }
                });
            } catch (RuntimeException e) {
                managed.pendingRestore = null;
            }
        }
        managedRadii.keySet().retainAll(onlineTrackers);
        if (managedRadii.isEmpty() && task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private synchronized void checkAndAdjust() {
        if (!registered) {
            return;
        }
        Set<ChunkTracker> onlineTrackers = new HashSet<>();
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef != null) {
                onlineTrackers.add(playerRef.getChunkTracker());
            }
        }
        managedRadii.keySet().retainAll(onlineTrackers);
        IdlePlayerService idleService = idlePlayerService;
        Set<World> activeWorlds = new HashSet<>();

        int lowestTarget = Integer.MAX_VALUE;
        float lowestTps = 0f;
        int appliedAtLowest = 0;

        for (World world : Universe.get().getWorlds().values()) {
            if (world.getPlayerRefs().isEmpty()) {
                continue;
            }
            activeWorlds.add(world);
            float worldTps = (float) TpsUtil.getWorldTps(world);
            int target = calculateTargetRadius(worldTps, (float) TpsUtil.getTargetTps(world));
            Integer previous = worldTargets.get(world);
            if (previous != null && target > previous) {
                target = previous + RADIUS_INCREASE_STEP;
            }
            worldTargets.put(world, target);
            int applied = applyToWorld(world, target, idleService);
            if (target < lowestTarget) {
                lowestTarget = target;
                lowestTps = worldTps;
                appliedAtLowest = applied;
            }
        }
        worldTargets.keySet().retainAll(activeWorlds);

        if (lowestTarget == Integer.MAX_VALUE) {
            return;
        }
        if (lowestTarget != currentTargetRadius) {
            if (appliedAtLowest > 0) {
                LOGGER.atInfo().log(
                        "Adjusted per-player hot radius: %d -> %d (TPS: %.1f, players: %d)",
                        currentTargetRadius, lowestTarget, lowestTps, appliedAtLowest);
                RefixesEvents.action(
                        "PerPlayerHotRadius",
                        String.format(
                                java.util.Locale.ROOT,
                                "radius %d to %d (TPS %.1f, players %d)",
                                currentTargetRadius,
                                lowestTarget,
                                lowestTps,
                                appliedAtLowest));
            }
            currentTargetRadius = lowestTarget;
        }
    }

    public int getCurrentTargetRadius() {
        return currentTargetRadius;
    }

    private static int calculateTargetRadius(float tps, float targetTps) {
        PerPlayerHotRadiusConfig config = PerPlayerHotRadiusConfig.get();
        int minRadius = config.getValue(PerPlayerHotRadiusConfig.MIN_RADIUS);
        int maxRadius = config.getValue(PerPlayerHotRadiusConfig.MAX_RADIUS);
        float tpsLow = (float) (config.getValue(PerPlayerHotRadiusConfig.TPS_LOW_FRACTION) * targetTps);
        float tpsHigh = (float) (config.getValue(PerPlayerHotRadiusConfig.TPS_HIGH_FRACTION) * targetTps);

        if (tps <= tpsLow) {
            return minRadius;
        } else if (tps >= tpsHigh) {
            return maxRadius;
        } else {
            float ratio = (tps - tpsLow) / (tpsHigh - tpsLow);
            int range = maxRadius - minRadius;
            return minRadius + (int) (range * ratio);
        }
    }

    private int applyToWorld(World world, int targetRadius, IdlePlayerService idleService) {
        List<PlayerRef> snapshot = new ArrayList<>();
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef == null) {
                continue;
            }
            // IdlePlayer owns the hot radius of idle players; don't fight it.
            if (idleService != null && idleService.isIdle(playerRef.getUuid())) {
                continue;
            }
            snapshot.add(playerRef);
        }
        if (snapshot.isEmpty()) {
            return 0;
        }
        try {
            world.execute(() -> {
                synchronized (PerPlayerHotRadiusService.this) {
                    if (!registered) {
                        return;
                    }
                    for (PlayerRef playerRef : snapshot) {
                        var reference = playerRef.getReference();
                        if (reference == null
                                || !reference.isValid()
                                || reference.getStore().getExternalData().getWorld() != world) {
                            continue;
                        }
                        if (idleService == null || !idleService.isIdle(playerRef.getUuid())) {
                            updateHotRadius(playerRef, targetRadius);
                        }
                    }
                }
            });
            return snapshot.size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean updateHotRadius(PlayerRef playerRef, int radius) {
        ChunkTracker tracker = playerRef.getChunkTracker();
        synchronized (tracker) {
            int currentRadius = tracker.getMaxHotLoadedRadius();
            long revision = PlayerRadiusRevisions.hot(tracker);
            ManagedRadius managed = managedRadii.get(tracker);
            if (managed == null) {
                managed =
                        new ManagedRadius(currentRadius, currentRadius != ChunkTracker.MAX_HOT_LOADED_RADIUS, revision);
                managedRadii.put(tracker, managed);
            } else if (currentRadius != managed.applied || revision != managed.revision) {
                managed.original = currentRadius;
                managed.capped = true;
            }
            int desired = managed.capped ? Math.min(radius, managed.original) : radius;
            managed.applied = desired;
            if (currentRadius == desired) {
                managed.revision = revision;
                return false;
            }
            tracker.setMaxHotLoadedRadius(desired);
            managed.revision = PlayerRadiusRevisions.hot(tracker);
            return true;
        }
    }

    private static final class ManagedRadius {
        int original;
        int applied;
        boolean capped;
        long revision;
        Ref<EntityStore> pendingRestore;

        ManagedRadius(int original, boolean capped, long revision) {
            this.original = original;
            this.applied = original;
            this.capped = capped;
            this.revision = revision;
        }
    }

    private void initMemoryGuard() {
        synchronized (memoryLock) {
            HytaleServerConfig config = HytaleServer.get().getConfig();
            synchronized (config) {
                int engineValue = config.getMaxViewRadius();
                Integer restored = readRestore();
                if (restored != null) {
                    // Leftover file means an unclean shutdown; it holds the true (pre-reduction) baseline.
                    initialMaxViewRadius = restored;
                    if (engineValue != restored) {
                        config.setMaxViewRadius(restored);
                    }
                } else {
                    initialMaxViewRadius = engineValue;
                    writeRestore(engineValue);
                }
                appliedMaxViewRadius = config.getMaxViewRadius();
                viewRadiusRevision = memoryViewRadiusRevision(config);
            }
            lastViewAdjustmentMs = System.currentTimeMillis();

            double threshold = PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.MEMORY_HEAP_THRESHOLD);
            heapMonitor = new HeapPressureMonitor();
            heapMonitor.start(threshold, this::onMemoryPressure);
        }
    }

    private void restoreMemoryGuard() {
        synchronized (memoryLock) {
            if (heapMonitor != null) {
                heapMonitor.stop();
                heapMonitor = null;
            }
            if (initialMaxViewRadius < 0) {
                return;
            }
            int restored = initialMaxViewRadius;
            try {
                HytaleServerConfig config = HytaleServer.get().getConfig();
                synchronized (config) {
                    currentMemoryViewRadius(config);
                    restored = initialMaxViewRadius;
                    if (config.getMaxViewRadius() != restored) {
                        config.setMaxViewRadius(restored);
                    }
                    initialMaxViewRadius = -1;
                }
                saveRestoredViewRadius(config);
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to restore max view radius to %d", restored);
            } finally {
                initialMaxViewRadius = -1;
            }
        }
    }

    private void onMemoryPressure() {
        synchronized (memoryLock) {
            if (initialMaxViewRadius < 0) {
                return;
            }
            HytaleServerConfig serverConfig = HytaleServer.get().getConfig();
            synchronized (serverConfig) {
                int current = currentMemoryViewRadius(serverConfig);
                PerPlayerHotRadiusConfig config = PerPlayerHotRadiusConfig.get();
                int minView = Math.min(
                        initialMaxViewRadius,
                        Math.max(1, config.getValue(PerPlayerHotRadiusConfig.MEMORY_MIN_VIEW_RADIUS)));
                double decreaseFactor = config.getValue(PerPlayerHotRadiusConfig.MEMORY_VIEW_DECREASE_FACTOR);
                long minStepMs = Math.max(1000L, config.getValue(PerPlayerHotRadiusConfig.CHECK_INTERVAL_MS));

                long now = System.currentTimeMillis();
                // Throttle the descent to at most one step per interval, even during a GC storm.
                if (now - lastViewAdjustmentMs < minStepMs) {
                    return;
                }
                int target = Math.max(minView, (int) Math.floor(decreaseFactor * current));
                if (target < current) {
                    setMemoryViewRadius(serverConfig, target);
                    LOGGER.atWarning().log("Memory pressure: reducing max view radius %d -> %d", current, target);
                    RefixesEvents.action(
                            "PerPlayerHotRadius",
                            String.format(java.util.Locale.ROOT, "memory view radius %d to %d", current, target));
                    lastViewAdjustmentMs = now;
                }
            }
        }
    }

    private void recoverViewRadius() {
        synchronized (memoryLock) {
            if (initialMaxViewRadius < 0) {
                return;
            }
            HytaleServerConfig serverConfig = HytaleServer.get().getConfig();
            synchronized (serverConfig) {
                int current = currentMemoryViewRadius(serverConfig);
                if (current >= initialMaxViewRadius) {
                    return;
                }
                PerPlayerHotRadiusConfig config = PerPlayerHotRadiusConfig.get();
                double threshold = config.getValue(PerPlayerHotRadiusConfig.MEMORY_HEAP_THRESHOLD);
                long recoveryWaitMs =
                        Math.max(1000L, config.getValue(PerPlayerHotRadiusConfig.MEMORY_RECOVERY_WAIT_SECONDS) * 1000L);

                long now = System.currentTimeMillis();
                if (now - lastViewAdjustmentMs < recoveryWaitMs) {
                    return;
                }
                if (heapMonitor != null && heapMonitor.currentAfterGcRatio() >= threshold) {
                    return;
                }
                int target = Math.min(current + MEMORY_VIEW_INCREASE_STEP, initialMaxViewRadius);
                if (target > current) {
                    setMemoryViewRadius(serverConfig, target);
                    LOGGER.atInfo().log("Memory recovered: increasing max view radius %d -> %d", current, target);
                    lastViewAdjustmentMs = now;
                }
            }
        }
    }

    private int currentMemoryViewRadius(HytaleServerConfig config) {
        int current = config.getMaxViewRadius();
        long revision = memoryViewRadiusRevision(config);
        if (current != appliedMaxViewRadius || revision != viewRadiusRevision) {
            initialMaxViewRadius = current;
            appliedMaxViewRadius = current;
            viewRadiusRevision = revision;
            writeRestore(current);
        }
        return current;
    }

    private void setMemoryViewRadius(HytaleServerConfig config, int radius) {
        config.setMaxViewRadius(radius);
        appliedMaxViewRadius = config.getMaxViewRadius();
        viewRadiusRevision = memoryViewRadiusRevision(config);
    }

    private static long memoryViewRadiusRevision(HytaleServerConfig config) {
        return Early.isEnabled() ? RadiusWrites.revision(config) : -1;
    }

    private static final class RadiusWrites {
        private static long revision(HytaleServerConfig config) {
            return config instanceof ServerViewRadiusAccess access ? access.refixes$getViewRadiusRevision() : -1;
        }
    }

    private static Integer readRestore() {
        try {
            if (!Files.isRegularFile(VIEW_RADIUS_RESTORE_FILE)) {
                return null;
            }
            String content = Files.readString(VIEW_RADIUS_RESTORE_FILE, StandardCharsets.UTF_8)
                    .trim();
            int radius = Integer.parseInt(content);
            if (radius <= 0 || radius > HytaleServerConfig.MAX_VIEW_RADIUS_LIMIT) {
                throw new IllegalArgumentException("Saved view radius is outside the server limits");
            }
            return radius;
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log("Failed to read saved max view radius");
            return null;
        }
    }

    private static void writeRestore(int value) {
        try {
            Files.createDirectories(VIEW_RADIUS_RESTORE_FILE.getParent());
            Files.writeString(VIEW_RADIUS_RESTORE_FILE, Integer.toString(value), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to save max view radius %d, so it cannot be restored after an unclean shutdown", value);
        }
    }

    private static void deleteRestore() {
        try {
            Files.deleteIfExists(VIEW_RADIUS_RESTORE_FILE);
        } catch (Exception e) {
            LOGGER.atWarning().withCause(e).log(
                    "Failed to delete the saved max view radius, so it is applied again at the next start");
        }
    }
}
