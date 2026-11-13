package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkUnloadingSystem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkUnloadingSystem.class)
public class MixinChunkUnloadingSystem {

    // Protect spawn chunk at origin (0,0)
    @WrapOperation(
            method = "tryUnload",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/WorldChunk;shouldKeepLoaded()Z"))
    private static boolean refixes$protectSpawnChunk(WorldChunk worldChunk, Operation<Boolean> original) {
        if (original.call(worldChunk)) {
            return true;
        }
        return EarlyOptions.isAvailable()
                && EarlyOptions.VANILLA_KEEP_SPAWN_LOADED.get()
                && worldChunk.getX() == 0
                && worldChunk.getZ() == 0;
    }
}
