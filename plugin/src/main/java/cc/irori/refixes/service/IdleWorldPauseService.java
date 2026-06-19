package cc.irori.refixes.service;

import cc.irori.refixes.compat.BlackboxBridge;
import cc.irori.refixes.config.impl.IdleWorldPauseConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class IdleWorldPauseService {

    private static final HytaleLogger LOGGER = Logs.logger();

    private ScheduledFuture<?> task;
    private AutoCloseable pausedGauge;

    public void registerService() {
        int interval = Math.max(1000, IdleWorldPauseConfig.get().getValue(IdleWorldPauseConfig.CHECK_INTERVAL_MS));
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        execute();
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error while pausing idle worlds");
                    }
                },
                5000,
                interval,
                TimeUnit.MILLISECONDS);
        pausedGauge = BlackboxBridge.registerGauge("IdleWorldPause paused worlds", () -> getPausedWorldCount());
    }

    public void unregisterService() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (pausedGauge != null) {
            try {
                pausedGauge.close();
            } catch (Exception ignored) {
            }
            pausedGauge = null;
        }
    }

    private void execute() {
        Set<String> excluded = new HashSet<>();
        for (String name : IdleWorldPauseConfig.get().getValue(IdleWorldPauseConfig.EXCLUDED_WORLDS)) {
            if (name != null) {
                excluded.add(name.toLowerCase(Locale.ROOT));
            }
        }
        Map<String, World> worldsByName = Universe.get().getWorlds();
        for (World world : worldsByName.values()) {
            if (excluded.contains(world.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (world.isStarted() && world.getPlayerCount() == 0 && !world.isPaused()) {
                world.execute(() -> {
                    if (world.getPlayerCount() == 0 && !world.isPaused()) {
                        world.setPaused(true);
                        BlackboxBridge.event("IdleWorldPause", "paused world '" + world.getName() + "'");
                    }
                });
            }
        }
    }

    public int getPausedWorldCount() {
        int paused = 0;
        for (World world : Universe.get().getWorlds().values()) {
            if (world.isPaused()) {
                paused++;
            }
        }
        return paused;
    }
}
