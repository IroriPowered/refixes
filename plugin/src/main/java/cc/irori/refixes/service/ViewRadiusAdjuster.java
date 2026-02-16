package cc.irori.refixes.service;

import cc.irori.refixes.config.impl.ViewRadiusAdjusterConfig;
import cc.irori.refixes.util.Logs;
import cc.irori.refixes.util.TpsUtil;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.common.semver.SemverRange;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import java.util.concurrent.ScheduledFuture;

public class ViewRadiusAdjuster {

    private static final PluginIdentifier PERFORMANCE_SAVER = new PluginIdentifier("Nitrado", "PerformanceSaver");
    private static final HytaleLogger LOGGER = Logs.logger();

    private final int initialViewRadius;

    private ScheduledFuture<?> task;

    public ViewRadiusAdjuster() {
        initialViewRadius = HytaleServer.get().getConfig().getMaxViewRadius();

        if (HytaleServer.get().getPluginManager().hasPlugin(PERFORMANCE_SAVER, SemverRange.WILDCARD)) {
            LOGGER.atWarning().log(
                    "Nitrado:PerformanceSaver plugin detected! View radius adjuster can conflict and players might experience flickering visuals.");
        }
    }

    public void registerService() {
        int interval =
                Math.max(1000, ViewRadiusAdjusterConfig.get().getValue(ViewRadiusAdjusterConfig.CHECK_INTERVAL_MS));
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        checkAndAdjust();
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error while adjusting view radius");
                    }
                },
                5000,
                interval,
                java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void unregisterService() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        HytaleServer.get().getConfig().setMaxViewRadius(initialViewRadius);
    }

    public void checkAndAdjust() {
        if (Universe.get().getPlayerCount() <= 0) {
            return;
        }

        ViewRadiusAdjusterConfig config = ViewRadiusAdjusterConfig.get();
        float currentTps = (float) TpsUtil.getLowestWorldTps();
        int currentRadius = HytaleServer.get().getConfig().getMaxViewRadius();
        int minRadius = Math.max(config.getValue(ViewRadiusAdjusterConfig.MIN_RADIUS), 1);
        int maxRadius = Math.max(config.getValue(ViewRadiusAdjusterConfig.MAX_RADIUS), minRadius);
        float lowTpsThreshold = config.getValue(ViewRadiusAdjusterConfig.DECREASE_RADIUS_TPS);
        float recoveryTpsThreshold = config.getValue(ViewRadiusAdjusterConfig.INCREASE_RADIUS_TPS);

        if (currentTps < lowTpsThreshold) {
            if (currentRadius > minRadius) {
                int newRadius = Math.max(minRadius, currentRadius - 1);
                applyViewRadius(
                        newRadius,
                        "Low TPS detected (" + String.format("%.2f", currentTps) + "). Reducing view radius");
            }
        } else if (currentTps > recoveryTpsThreshold) {
            if (currentRadius < maxRadius && currentRadius < initialViewRadius) {
                int newRadius = Math.min(initialViewRadius, currentRadius + 1);
                applyViewRadius(
                        newRadius, "TPS stable (" + String.format("%.2f", currentTps) + "). Increasing view radius");
            }
        }
    }

    private static void applyViewRadius(int radius, String reason) {
        HytaleServer.get().getConfig().setMaxViewRadius(radius);
        LOGGER.atWarning().log(reason);
        LOGGER.atWarning().log("View radius set to %d chunks", radius);
    }
}
