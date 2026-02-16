package cc.irori.refixes.service;

import cc.irori.refixes.config.impl.PerPlayerHotRadiusConfig;
import cc.irori.refixes.util.Logs;
import cc.irori.refixes.util.TpsUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PerPlayerHotRadiusService {

    private static final HytaleLogger LOGGER = Logs.logger();

    private ScheduledFuture<?> task;
    private int currentTargetRadius;

    public PerPlayerHotRadiusService() {
        currentTargetRadius = PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.MAX_RADIUS);
    }

    public void registerService() {
        int interval =
                Math.max(1000, PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.CHECK_INTERVAL_MS));
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        checkAndAdjust();
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error while adjusting per-player hot radius");
                    }
                },
                5000,
                interval,
                TimeUnit.MILLISECONDS);
    }

    public void unregisterService() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private void checkAndAdjust() {
        float currentTps = (float) TpsUtil.getLowestWorldTps();
        int targetRadius = calculateTargetRadius(currentTps);

        if (targetRadius != currentTargetRadius) {
            int applied = applyToAllPlayers(targetRadius);
            if (applied > 0) {
                LOGGER.atInfo().log(
                        "Adjusted per-player hot radius: %d -> %d (TPS: %.1f, players: %d)",
                        currentTargetRadius, targetRadius, currentTps, applied);
            }
            currentTargetRadius = targetRadius;
        }
    }

    private static int calculateTargetRadius(float tps) {
        PerPlayerHotRadiusConfig config = PerPlayerHotRadiusConfig.get();
        int minRadius = config.getValue(PerPlayerHotRadiusConfig.MIN_RADIUS);
        int maxRadius = config.getValue(PerPlayerHotRadiusConfig.MAX_RADIUS);
        float tpsLow = config.getValue(PerPlayerHotRadiusConfig.TPS_LOW);
        float tpsHigh = config.getValue(PerPlayerHotRadiusConfig.TPS_HIGH);

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

    private static int applyToAllPlayers(int targetRadius) {
        PerPlayerHotRadiusConfig config = PerPlayerHotRadiusConfig.get();
        int minRadius = config.getValue(PerPlayerHotRadiusConfig.MIN_RADIUS);
        int maxRadius = config.getValue(PerPlayerHotRadiusConfig.MAX_RADIUS);

        List<PlayerRef> players = Universe.get().getPlayers();
        if (players.isEmpty()) {
            return 0;
        }

        int applied = 0;
        int clamped = Math.clamp(targetRadius, minRadius, maxRadius);

        for (PlayerRef playerRef : players) {
            if (playerRef != null && updateHotRadius(playerRef, clamped)) {
                applied++;
            }
        }

        return applied;
    }

    private static boolean updateHotRadius(PlayerRef playerRef, int radius) {
        ChunkTracker tracker = playerRef.getChunkTracker();
        if (tracker.getMaxHotLoadedChunksRadius() == radius) {
            return false;
        }
        tracker.setMaxHotLoadedChunksRadius(radius);
        return true;
    }
}
