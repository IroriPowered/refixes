package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSavingSystems.class)
public class MixinChunkSavingSystems {

    // TODO: use ChunkSaveEvent (no mixins) to handle this

    @Inject(
            method = "tryQueue",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/ArchetypeChunk;getReferenceTo(I)Lcom/hypixel/hytale/component/Ref;"),
            cancellable = true)
    private static void refixes$skipSavingSharedInstanceChunks(
            int index,
            ArchetypeChunk<ChunkStore> archetypeChunk,
            Store<ChunkStore> store,
            CallbackInfo ci,
            @Local(name = "worldChunkComponent") WorldChunk worldChunkComponent) {
        if (worldChunkComponent.is(ChunkFlag.ON_DISK)) {
            World world = store.getExternalData().getWorld();
            if (world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
                worldChunkComponent.consumeNeedsSaving();
                ci.cancel();
            }
        }
    }

    @Inject(
            method = "tryQueueSync",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/ArchetypeChunk;getReferenceTo(I)Lcom/hypixel/hytale/component/Ref;"),
            cancellable = true)
    private static void refixes$skipSavingSharedInstanceChunksSync(
            ArchetypeChunk<ChunkStore> archetypeChunk,
            CommandBuffer<ChunkStore> commandBuffer,
            CallbackInfo ci,
            @Local(name = "worldChunkComponent") WorldChunk worldChunkComponent) {
        if (worldChunkComponent.is(ChunkFlag.ON_DISK)) {
            World world = commandBuffer.getExternalData().getWorld();
            if (world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
                worldChunkComponent.consumeNeedsSaving();
                ci.cancel();
            }
        }
    }
}
