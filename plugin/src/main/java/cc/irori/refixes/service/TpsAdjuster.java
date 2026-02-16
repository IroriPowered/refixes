package cc.irori.refixes.service;

import cc.irori.refixes.config.impl.TpsAdjusterConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TpsAdjuster {

    private static final String DEFAULT_WORLD = "__DEFAULT";
    private static final HytaleLogger LOGGER = Logs.logger();

    private ScheduledFuture<?> task;
    private long lastPlayerSeenAt;

    public void registerService() {
        lastPlayerSeenAt = System.currentTimeMillis();

        int interval = Math.max(1000, TpsAdjusterConfig.get().getValue(TpsAdjusterConfig.CHECK_INTERVAL_MS));
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        execute();
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error while adjusting TPS");
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
        setTps(TickingThread.TPS);
    }

    private void execute() {
        TpsAdjusterConfig config = TpsAdjusterConfig.get();
        int tpsLimit = config.getValue(TpsAdjusterConfig.TPS_LIMIT);
        int hibernateTps = config.getValue(TpsAdjusterConfig.HIBERNATE_TPS);
        int hibernateDelay = config.getValue(TpsAdjusterConfig.HIBERNATE_DELAY_MS);

        long now = System.currentTimeMillis();

        if (getPlayerCount() > 0) {
            this.lastPlayerSeenAt = now;
        }

        int targetTps = tpsLimit;
        if (now - lastPlayerSeenAt > hibernateDelay) {
            targetTps = hibernateTps;
        }

        setTps(targetTps);
    }

    private void setTps(int tps) {
        TpsAdjusterConfig config = TpsAdjusterConfig.get();
        String[] worldNameArray = config.getValue(TpsAdjusterConfig.WORLD_FILTER);

        Set<String> worldFilter = new HashSet<>();
        if (worldNameArray != null) {
            for (String name : worldNameArray) {
                if (name != null && !name.isBlank()) {
                    worldFilter.add(name);
                }
            }
        }

        if (worldFilter.contains(DEFAULT_WORLD)) {
            worldFilter.add(Universe.get().getDefaultWorld().getName());
        }

        for (var entry : Universe.get().getWorlds().entrySet()) {
            if (!worldFilter.isEmpty() && !worldFilter.contains(entry.getKey())) {
                continue;
            }

            World world = entry.getValue();
            if (world.getTps() != tps) {
                LOGGER.atInfo().log("Setting TPS of world %s to %d", world.getName(), tps);
                CompletableFuture.runAsync(() -> world.setTps(tps), world);
            }
        }
    }

    private int getPlayerCount() {
        int universePlayerCount = Universe.get().getPlayerCount();
        int worldSumPlayerCount = 0;
        for (var worldEntry : Universe.get().getWorlds().entrySet()) {
            worldSumPlayerCount += worldEntry.getValue().getPlayerCount();
        }
        return Math.max(universePlayerCount, worldSumPlayerCount);
    }
}
