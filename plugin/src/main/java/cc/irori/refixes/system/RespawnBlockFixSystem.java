package cc.irori.refixes.system;

import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerConfigData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerRespawnPointData;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.state.RespawnBlock;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

public class RespawnBlockFixSystem extends RefSystem<ChunkStore> {

    private static final HytaleLogger LOGGER = Logs.logger();

    @Override
    public void onEntityAdded(
            @NonNullDecl Ref<ChunkStore> ref,
            @NonNullDecl AddReason addReason,
            @NonNullDecl Store<ChunkStore> store,
            @NonNullDecl CommandBuffer<ChunkStore> commandBuffer) {
        // No-op
    }

    @Override
    public void onEntityRemove(
            @NonNullDecl Ref<ChunkStore> ref,
            @NonNullDecl RemoveReason removeReason,
            @NonNullDecl Store<ChunkStore> store,
            @NonNullDecl CommandBuffer<ChunkStore> commandBuffer) {
        if (removeReason == RemoveReason.UNLOAD) {
            return;
        }

        try {
            RespawnBlock respawnBlock = store.getComponent(ref, RespawnBlock.getComponentType());
            if (respawnBlock == null) return;
            UUID owner = respawnBlock.getOwnerUUID();
            if (owner == null) return;
            PlayerRef playerRef = Universe.get().getPlayer(owner);
            if (playerRef == null || !playerRef.isValid()) return;
            Holder<EntityStore> holder = playerRef.getHolder();
            if (holder == null) return;
            Player player = holder.getComponent(Player.getComponentType());
            if (player == null) return;
            PlayerConfigData configData = player.getPlayerConfigData();
            World world = store.getExternalData().getWorld();
            PlayerWorldData worldData = configData.getPerWorldData(world.getName());

            if (worldData.getRespawnPoints() == null) {
                worldData.setRespawnPoints(new PlayerRespawnPointData[0]);

                LOGGER.atWarning().log(
                        "Set respawn point to null for player %s in world %s",
                        playerRef.getUsername(), world.getName());
            }
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to apply respawn block fix");
        }
    }

    @NullableDecl
    @Override
    public Query<ChunkStore> getQuery() {
        return RespawnBlock.getComponentType();
    }
}
