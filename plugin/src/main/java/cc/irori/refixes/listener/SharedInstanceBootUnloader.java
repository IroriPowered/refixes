package cc.irori.refixes.listener;

import cc.irori.refixes.early.util.SharedInstanceConstants;
import cc.irori.refixes.system.SharedInstancePersistenceSystem;
import cc.irori.refixes.util.Early;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class SharedInstanceBootUnloader {

    private static final long UNLOAD_DELAY_MS = 1000L;
    private static final HytaleLogger LOGGER = Logs.logger();

    private final AtomicBoolean unloaded = new AtomicBoolean(false);
    private final Map<World, Boolean> bootWorlds = new ConcurrentHashMap<>();
    private final Set<UUID> unloadedWorlds = ConcurrentHashMap.newKeySet();

    public SharedInstanceBootUnloader() {
        Early.requireEnabled();
    }

    public void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry().register(AllWorldsLoadedEvent.class, event -> onAllWorldsLoaded());
        plugin.getEventRegistry()
                .registerAsyncGlobal(
                        EventPriority.LAST,
                        PlayerConnectEvent.class,
                        future -> future.thenApply(this::onPlayerConnect));
    }

    private void onAllWorldsLoaded() {
        if (!unloaded.compareAndSet(false, true)) {
            return;
        }

        for (World world : Universe.get().getWorlds().values()) {
            if (world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
                bootWorlds.put(world, false);
            }
        }
        HytaleServer.SCHEDULED_EXECUTOR.schedule(this::unloadSharedWorlds, UNLOAD_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private PlayerConnectEvent onPlayerConnect(PlayerConnectEvent event) {
        World world = event.getWorld();
        if (world == null || bootWorlds.remove(world, false)) {
            return event;
        }
        if (bootWorlds.containsKey(world)
                || (unloadedWorlds.contains(world.getWorldConfig().getUuid())
                        && Universe.get().getWorld(world.getName()) != world)) {
            World fallback = Universe.get().getDefaultWorld();
            if (fallback != null && fallback != world) {
                event.getHolder().tryRemoveComponent(TransformComponent.getComponentType());
                event.setWorld(fallback);
            }
        }
        return event;
    }

    private void unloadSharedWorlds() {
        int removed = 0;
        for (World world : bootWorlds.keySet()) {
            try {
                if (world.getPlayerCount() > 0) {
                    LOGGER.atInfo().log("Shared instance world '%s' has players, skipping unload", world.getName());
                    continue;
                }
                if (Universe.get().getWorld(world.getName()) != world || !bootWorlds.replace(world, false, true)) {
                    continue;
                }
                unloadedWorlds.add(world.getWorldConfig().getUuid());
                SharedInstancePersistenceSystem.preserveStorage(world.getWorldConfig());
                if (Universe.get().removeWorld(world.getName())) {
                    removed++;
                }
            } catch (Throwable t) {
                LOGGER.atWarning().withCause(t).log("Failed to unload shared instance world '%s'", world.getName());
            } finally {
                bootWorlds.remove(world);
            }
        }

        if (removed == 0) {
            LOGGER.atInfo().log("No shared instance worlds to unload");
            return;
        }
        LOGGER.atInfo().log("Unloaded %d shared instance world(s)", removed);
    }
}
