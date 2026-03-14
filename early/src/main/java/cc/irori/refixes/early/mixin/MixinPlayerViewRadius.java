package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class MixinPlayerViewRadius {

    @Shadow
    public abstract ChunkTracker getChunkTracker();

    @Inject(method = "setViewRadius", at = @At("TAIL"))
    private void refixes$updateMinLoadedRadius(int viewRadius, CallbackInfo ci) {
        if (!EarlyOptions.isAvailable()) {
            return;
        }

        ChunkTracker chunkTracker = this.getChunkTracker();
        if (chunkTracker != null) {
            int offset = EarlyOptions.CHUNK_UNLOAD_OFFSET.get();
            chunkTracker.setMinLoadedChunksRadius(viewRadius + offset);
        }
    }
}
