package cc.irori.refixes.service;

import cc.irori.refixes.compat.HyboxBridge;
import cc.irori.refixes.config.impl.IdleWorldPauseConfig;
import cc.irori.refixes.early.accessor.WorldPauseOwnerAccess;
import cc.irori.refixes.util.Early;
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

    private volatile Object owner;
    private ScheduledFuture<?> task;
    private AutoCloseable pausedGauge;

    public void registerService() {
        owner = new Object();
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
        pausedGauge = HyboxBridge.registerGauge("IdleWorldPause paused worlds", () -> getPausedWorldCount());
    }

    public void unregisterService() {
        Object generation = owner;
        owner = null;
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (Early.isEnabled()) Owned.restore(generation);
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
        Object generation = owner;
        boolean owned = Early.isEnabled();
        for (World world : worldsByName.values()) {
            if (excluded.contains(world.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (world.isStarted() && world.getPlayerCount() == 0 && !world.isPaused()) {
                world.execute(() -> {
                    if (generation != null && generation == owner && world.getPlayerCount() == 0 && !world.isPaused()) {
                        if (owned) Owned.pause(world, generation);
                        else world.setPaused(true);
                        HyboxBridge.event("IdleWorldPause", "paused world '" + world.getName() + "'");
                    }
                });
            }
        }
    }

    private static final class Owned {

        private static void restore(Object generation) {
            for (World world : Universe.get().getWorlds().values()) {
                if (!(world instanceof WorldPauseOwnerAccess access)) continue;
                try {
                    world.execute(() -> {
                        if (access.refixes$isPausedBy(generation)) access.refixes$setPaused(false, null);
                    });
                } catch (RuntimeException ignored) {
                }
            }
        }

        private static void pause(World world, Object generation) {
            if (world instanceof WorldPauseOwnerAccess access) access.refixes$setPaused(true, generation);
            else world.setPaused(true);
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
