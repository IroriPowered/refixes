package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerChunkTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Applies configurable MaxChunksPerSecond and MaxChunksPerTick to ChunkTracker on player add
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

        chunkTracker.setMaxChunksPerSecond(EarlyOptions.MAX_CHUNKS_PER_SECOND.get());
        chunkTracker.setMaxChunksPerTick(EarlyOptions.MAX_CHUNKS_PER_TICK.get());
    }
}
