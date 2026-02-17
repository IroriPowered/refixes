package cc.irori.refixes.listener;

import cc.irori.refixes.early.util.SharedInstanceConstants;
import cc.irori.refixes.util.Early;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SharedInstanceBootUnloader {

    private static final long UNLOAD_DELAY_MS = 1000L;
    private static final HytaleLogger LOGGER = Logs.logger();

    private final AtomicBoolean unloaded = new AtomicBoolean(false);

    public SharedInstanceBootUnloader() {
        Early.requireEnabled();
    }

    public void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry().register(AllWorldsLoadedEvent.class, event -> onAllWorldsLoaded());
    }

    private void onAllWorldsLoaded() {
        if (!unloaded.compareAndSet(false, true)) {
            return;
        }

        HytaleServer.SCHEDULED_EXECUTOR.schedule(this::unloadSharedWorlds, UNLOAD_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private void unloadSharedWorlds() {
        Map<String, World> worlds = Universe.get().getWorlds();
        if (worlds.isEmpty()) {
            return;
        }

        List<String> toUnload = new ArrayList<>();
        for (World world : worlds.values()) {
            if (!world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
                continue;
            }
            if (world.getPlayerCount() > 0) {
                LOGGER.atInfo().log("Shared instance world '%s' has players, skipping unload", world.getName());
                continue;
            }
            toUnload.add(world.getName());
        }

        if (toUnload.isEmpty()) {
            LOGGER.atInfo().log("No shared instance worlds to unload");
            return;
        }

        int removed = 0;
        for (String worldName : toUnload) {
            try {
                if (Universe.get().removeWorld(worldName)) {
                    removed++;
                }
            } catch (Throwable t) {
                LOGGER.atWarning().withCause(t).log("Failed to unload shared instance world '%s'", worldName);
            }
        }

        LOGGER.atInfo().log("Unloaded %d shared instance world(s)", removed);
    }
}
