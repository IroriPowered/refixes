package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkUnloadingSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkUnloadingSystem.class)
public class MixinChunkUnloadingSystem {

    @Inject(
            method = "tryUnload",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/WorldChunk;pollKeepAlive(I)I"),
            cancellable = true)
    private static void refixes$protectSpawnChunk(
            int index,
            ArchetypeChunk<ChunkStore> archetypeChunk,
            CommandBuffer<ChunkStore> commandBuffer,
            CallbackInfo ci) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.VANILLA_KEEP_SPAWN_LOADED.get()) {
            return;
        }

        WorldChunk worldChunk = archetypeChunk.getComponent(index, WorldChunk.getComponentType());

        // Protect spawn chunk at origin (0,0)
        if (worldChunk.getX() == 0 && worldChunk.getZ() == 0) {
            worldChunk.resetKeepAlive();
            ci.cancel();
        }
    }
}
