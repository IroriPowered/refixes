package cc.irori.refixes.service;

import cc.irori.refixes.config.impl.IdleWorldPauseConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class IdleWorldPauseService {

    private static final HytaleLogger LOGGER = Logs.logger();

    private ScheduledFuture<?> task;

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
    }

    public void unregisterService() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    private void execute() {
        Set<String> excluded =
                new HashSet<>(Arrays.asList(IdleWorldPauseConfig.get().getValue(IdleWorldPauseConfig.EXCLUDED_WORLDS)));
        Map<String, World> worldsByName = Universe.get().getWorlds();
        for (World world : worldsByName.values()) {
            if (excluded.contains(world.getName())) {
                continue;
            }
            if (world.isStarted() && world.getPlayerCount() == 0 && !world.isPaused()) {
                world.execute(() -> {
                    if (world.getPlayerCount() == 0 && !world.isPaused()) {
                        world.setPaused(true);
                    }
                });
            }
        }
    }
}
