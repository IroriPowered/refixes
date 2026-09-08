package cc.irori.refixes.service;

import cc.irori.refixes.compat.BlackboxBridge;
import cc.irori.refixes.config.impl.PerPlayerHotRadiusConfig;
import cc.irori.refixes.util.HeapPressureMonitor;
import cc.irori.refixes.util.Logs;
import cc.irori.refixes.util.TpsUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
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
    private static final Path VIEW_RADIUS_RESTORE_FILE =
            Paths.get("mods", "IroriPowered_Refixes", "viewradius-restore");

    private ScheduledFuture<?> task;
    private boolean registered;
    private final Map<ChunkTracker, Integer> originalHotRadii = new HashMap<>();
    private volatile int currentTargetRadius;
    private volatile IdlePlayerService idlePlayerService;
    private AutoCloseable radiusGauge;

    private final Object memoryLock = new Object();
    private HeapPressureMonitor heapMonitor;
    private int initialMaxViewRadius = -1;
    private long lastViewAdjustmentMs = 0L;

    public PerPlayerHotRadiusService() {
        currentTargetRadius = PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.MAX_RADIUS);
    }

    public void setIdlePlayerService(IdlePlayerService idlePlayerService) {
        this.idlePlayerService = idlePlayerService;
    }

    public synchronized void registerService() {
        if (registered) {
            return;
        }
        registered = true;
        if (PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.MEMORY_GUARD_ENABLED)) {
            try {
                initMemoryGuard();
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log("Failed to initialize memory view-radius guard");
            }
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
        radiusGauge = BlackboxBridge.registerGauge("PerPlayerHotRadius radius", () -> getCurrentTargetRadius());
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
        IdlePlayerService idleService = idlePlayerService;
        for (World world : Universe.get().getWorlds().values()) {
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                if (playerRef == null) {
                    continue;
                }
                Integer originalRadius = originalHotRadii.get(playerRef.getChunkTracker());
                if (originalRadius == null) {
                    continue;
                }
                try {
                    world.execute(() -> {
                        if (idleService != null) {
                            idleService.restoreHotRadius(playerRef, originalRadius);
                        } else {
                            playerRef.getChunkTracker().setMaxHotLoadedRadius(originalRadius);
                        }
                    });
                } catch (RuntimeException e) {
                    LOGGER.atWarning().withCause(e).log(
                            "Failed to queue hot-radius restoration for player %s", playerRef.getUuid());
                }
            }
        }
        originalHotRadii.clear();
        restoreMemoryGuard();
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
        originalHotRadii.keySet().retainAll(onlineTrackers);
        IdlePlayerService idleService = idlePlayerService;

        int lowestTarget = Integer.MAX_VALUE;
        float lowestTps = 0f;
        int appliedAtLowest = 0;

        for (World world : Universe.get().getWorlds().values()) {
            if (world.getPlayerRefs().isEmpty()) {
                continue;
            }
            float worldTps = (float) TpsUtil.getWorldTps(world);
            int target = calculateTargetRadius(worldTps, (float) TpsUtil.getTargetTps(world));
            int applied = applyToWorld(world, target, idleService);
            if (target < lowestTarget) {
                lowestTarget = target;
                lowestTps = worldTps;
                appliedAtLowest = applied;
            }
        }

        if (lowestTarget == Integer.MAX_VALUE) {
            return;
        }
        if (lowestTarget != currentTargetRadius) {
            if (appliedAtLowest > 0) {
                LOGGER.atInfo().log(
                        "Adjusted per-player hot radius: %d -> %d (TPS: %.1f, players: %d)",
                        currentTargetRadius, lowestTarget, lowestTps, appliedAtLowest);
                BlackboxBridge.event(
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
        int currentRadius = tracker.getMaxHotLoadedRadius();
        if (currentRadius == radius) {
            return false;
        }
        originalHotRadii.putIfAbsent(tracker, currentRadius);
        tracker.setMaxHotLoadedRadius(radius);
        return true;
    }

    private void initMemoryGuard() {
        synchronized (memoryLock) {
            int engineValue = HytaleServer.get().getConfig().getMaxViewRadius();
            Integer restored = readRestore();
            if (restored != null) {
                // Leftover file means an unclean shutdown; it holds the true (pre-reduction) baseline.
                initialMaxViewRadius = restored;
                if (engineValue != restored) {
                    HytaleServer.get().getConfig().setMaxViewRadius(restored);
                }
            } else {
                initialMaxViewRadius = engineValue;
                writeRestore(engineValue);
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
            try {
                if (HytaleServer.get().getConfig().getMaxViewRadius() != initialMaxViewRadius) {
                    HytaleServer.get().getConfig().setMaxViewRadius(initialMaxViewRadius);
                }
            } catch (Exception ignored) {
            }
            deleteRestore();
            initialMaxViewRadius = -1;
        }
    }

    private void onMemoryPressure() {
        synchronized (memoryLock) {
            if (initialMaxViewRadius < 0) {
                return;
            }
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
            int current = HytaleServer.get().getConfig().getMaxViewRadius();
            int target = Math.max(minView, (int) Math.floor(decreaseFactor * current));
            if (target < current) {
                HytaleServer.get().getConfig().setMaxViewRadius(target);
                LOGGER.atWarning().log("Memory pressure: reducing max view radius %d -> %d", current, target);
                BlackboxBridge.event(
                        "PerPlayerHotRadius",
                        String.format(java.util.Locale.ROOT, "memory view radius %d to %d", current, target));
                lastViewAdjustmentMs = now;
            }
        }
    }

    private void recoverViewRadius() {
        synchronized (memoryLock) {
            if (initialMaxViewRadius < 0) {
                return;
            }
            int current = HytaleServer.get().getConfig().getMaxViewRadius();
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
                HytaleServer.get().getConfig().setMaxViewRadius(target);
                LOGGER.atInfo().log("Memory recovered: increasing max view radius %d -> %d", current, target);
                lastViewAdjustmentMs = now;
            }
        }
    }

    private static Integer readRestore() {
        try {
            if (!Files.isRegularFile(VIEW_RADIUS_RESTORE_FILE)) {
                return null;
            }
            String content = Files.readString(VIEW_RADIUS_RESTORE_FILE, StandardCharsets.UTF_8)
                    .trim();
            return content.isEmpty() ? null : Integer.parseInt(content);
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeRestore(int value) {
        try {
            Files.createDirectories(VIEW_RADIUS_RESTORE_FILE.getParent());
            Files.writeString(VIEW_RADIUS_RESTORE_FILE, Integer.toString(value), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static void deleteRestore() {
        try {
            Files.deleteIfExists(VIEW_RADIUS_RESTORE_FILE);
        } catch (Exception ignored) {
        }
    }
}
