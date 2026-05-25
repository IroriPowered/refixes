package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class MixinPlayerViewRadius {

    @Shadow
    private PlayerRef playerRef;

    @Inject(method = "setClientViewRadius", at = @At("TAIL"))
    private void refixes$updateMinLoadedRadius(int viewRadius, CallbackInfo ci) {
        if (!EarlyOptions.isAvailable()) {
            return;
        }

        Ref<EntityStore> ref = this.playerRef.getReference();
        if (ref == null) {
            return;
        }

        ChunkTracker chunkTracker = ref.getStore().getComponent(ref, ChunkTracker.getComponentType());
        if (chunkTracker != null) {
            int offset = EarlyOptions.CHUNK_UNLOAD_OFFSET.get();
            chunkTracker.setMinLoadedChunksRadius(viewRadius + offset);
        }
    }
}
