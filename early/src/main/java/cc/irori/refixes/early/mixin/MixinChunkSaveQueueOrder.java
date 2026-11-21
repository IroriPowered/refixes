package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.ChunkSaveQueue;
import cc.irori.refixes.early.util.PatchCounters;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSavingSystems.class)
public class MixinChunkSaveQueueOrder {

    @WrapOperation(
            method = {"tryQueue", "tryQueueSync"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/component/ChunkSavingSystems$Data;push(Lcom/hypixel/hytale/component/Ref;)V"),
            require = 2,
            allow = 2)
    private static void refixes$queueBehind(
            ChunkSavingSystems.Data data, Ref<ChunkStore> reference, Operation<Void> original) {
        boolean behind = ((ChunkSaveQueue) data).refixes$enqueueLast(reference);
        if (PatchCounters.ENABLED) {
            PatchCounters.CHUNK_SAVE_QUEUE_ORDER.countReached();
            if (behind) PatchCounters.CHUNK_SAVE_QUEUE_ORDER.countEffect();
        }
    }
}
