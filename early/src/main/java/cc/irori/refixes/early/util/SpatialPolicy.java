package cc.irori.refixes.early.util;

import com.hypixel.hytale.builtin.path.PathSpatialSystem;
import com.hypixel.hytale.server.core.modules.collision.TangiableEntitySpatialSystem;
import com.hypixel.hytale.server.core.modules.entity.system.EntitySpatialSystem;
import com.hypixel.hytale.server.core.modules.entity.system.ItemSpatialSystem;
import com.hypixel.hytale.server.core.modules.entity.system.NetworkSendableSpatialSystem;
import com.hypixel.hytale.server.core.modules.entity.system.PlayerSpatialSystem;
import com.hypixel.hytale.server.npc.systems.NPCSpatialSystem;
import com.hypixel.hytale.server.spawning.systems.BeaconSpatialSystem;
import com.hypixel.hytale.server.spawning.systems.LegacyBeaconSpatialSystem;
import com.hypixel.hytale.server.spawning.systems.SpawnMarkerSpatialSystem;

public final class SpatialPolicy {
    private SpatialPolicy() {}

    public static boolean parallelSafe(Class<?> type) {
        return type == PathSpatialSystem.class
                || type == TangiableEntitySpatialSystem.class
                || type == EntitySpatialSystem.class
                || type == ItemSpatialSystem.class
                || type == NetworkSendableSpatialSystem.class
                || type == PlayerSpatialSystem.class
                || type == NPCSpatialSystem.class
                || type == BeaconSpatialSystem.class
                || type == LegacyBeaconSpatialSystem.class
                || type == SpawnMarkerSpatialSystem.class;
    }
}
