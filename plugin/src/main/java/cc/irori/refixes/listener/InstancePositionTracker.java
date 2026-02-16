package cc.irori.refixes.listener;

import cc.irori.refixes.util.ECSUtil;
import cc.irori.refixes.util.Logs;
import cc.irori.refixes.util.WeakLocation;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class InstancePositionTracker {

    private static final HytaleLogger LOGGER = Logs.logger();

    private final Cache<UUID, WeakLocation> savedLocations =
            CacheBuilder.newBuilder().expireAfterAccess(2, TimeUnit.HOURS).build();

    public void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry().registerGlobal(DrainPlayerFromWorldEvent.class, this::onDrainPlayerFromWorld);
        plugin.getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

        LOGGER.atInfo().log("Instance position tracker is enabled");
    }

    private void onDrainPlayerFromWorld(DrainPlayerFromWorldEvent event) {
        World world = event.getWorld();
        String worldName = world.getName();
        UUID playerUuid = ECSUtil.getPlayerUuid(event.getHolder());
        if (playerUuid == null) return;
        PlayerRef playerRef = event.getHolder().getComponent(PlayerRef.getComponentType());
        if (playerRef == null) return;

        // Player leaves a normal world
        if (!worldName.startsWith(InstancesPlugin.INSTANCE_PREFIX)) {
            savedLocations.put(playerUuid, new WeakLocation(worldName, event.getTransform()));

            LOGGER.atInfo().log("Saved location for player %s in world '%s' (instance entry?)", playerUuid, worldName);
            return;
        } else { // Player leaves an instance world
            World returnWorld = null;
            try {
                returnWorld = event.getWorld();
            } catch (Exception ignored) {
            }

            if (returnWorld != null) {
                savedLocations.invalidate(playerUuid);
                return;
            }

            WeakLocation location = savedLocations.getIfPresent(playerUuid);
            if (location == null) return;

            returnWorld = location.getWorld();
            if (returnWorld == null) {
                LOGGER.atWarning().log(
                        "Instance return world '%s' for player %s does not exist!",
                        location.worldName(), playerRef.getUsername());
                return;
            }
            event.setWorld(returnWorld);
            event.setTransform(location.getTransform());

            LOGGER.atWarning().log("Restored instance return world for player %s", playerRef.getUsername());
            savedLocations.invalidate(playerUuid);
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        savedLocations.invalidate(uuid);
    }
}
