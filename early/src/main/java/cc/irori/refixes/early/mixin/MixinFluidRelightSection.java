package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.fluid.FluidSystems;
import com.hypixel.hytale.server.core.universe.world.lighting.ChunkLightingManager;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FluidSystems.ReplicateChanges.class)
public abstract class MixinFluidRelightSection {

    @WrapOperation(
            method =
                    "lambda$tick$0(Lcom/hypixel/hytale/server/core/universe/world/chunk/WorldChunk;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/component/Store;III)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/lighting/ChunkLightingManager;invalidateLightInChunkSection(Lcom/hypixel/hytale/server/core/universe/world/storage/ChunkGrid;III)Z"),
            require = 1,
            allow = 1)
    private static boolean refixes$relightChangedSection(
            ChunkLightingManager lighting,
            ChunkGrid grid,
            int sectionX,
            int sectionY,
            int sectionZ,
            Operation<Boolean> original) {
        return original.call(lighting, grid, sectionX, sectionZ, sectionY);
    }
}
