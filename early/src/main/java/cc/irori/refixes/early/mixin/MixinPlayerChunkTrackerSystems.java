package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerChunkTrackerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerChunkTrackerSystems.AddSystem.class)
public class MixinPlayerChunkTrackerSystems {

    @Inject(method = "onEntityAdd", at = @At("TAIL"))
    private void refixes$applyChunkRateLimits(
            Holder<EntityStore> holder, AddReason reason, Store<EntityStore> store, CallbackInfo ci) {
        if (!EarlyOptions.isAvailable()) {
            return;
        }

        ChunkTracker chunkTracker = holder.getComponent(ChunkTracker.getComponentType());
        if (chunkTracker == null) {
            return;
        }

        PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
        chunkTracker.setMaxSectionsPerSecond(refixes$maxSectionsPerSecond(playerRef));
        chunkTracker.setMaxSectionsPerTick(EarlyOptions.MAX_SECTIONS_PER_TICK.get());

        Player player = holder.getComponent(Player.getComponentType());
        if (player != null) {
            int viewRadius = player.getViewRadius();
            int offset = EarlyOptions.CHUNK_UNLOAD_OFFSET.get();
            chunkTracker.setMinLoadedRadius(viewRadius + offset);
        }
    }

    @Unique
    private static int refixes$maxSectionsPerSecond(PlayerRef playerRef) {
        PacketHandler handler = playerRef != null ? playerRef.getPacketHandler() : null;
        if (handler != null && handler.isLocalConnection()) {
            return EarlyOptions.MAX_SECTIONS_PER_SECOND_LOCAL.get();
        }
        if (handler != null && handler.isLANConnection()) {
            return EarlyOptions.MAX_SECTIONS_PER_SECOND_LAN.get();
        }
        return EarlyOptions.MAX_SECTIONS_PER_SECOND.get();
    }
}
