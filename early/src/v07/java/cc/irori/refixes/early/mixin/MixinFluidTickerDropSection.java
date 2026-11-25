package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.server.core.asset.type.fluid.FluidTicker;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(FluidTicker.class)
public abstract class MixinFluidTickerDropSection {

    private static final String DISTANCE_TO_DROP =
            "distanceToDrop(Lcom/hypixel/hytale/assetstore/map/BlockTypeAssetMap;Lcom/hypixel/hytale/server/core/asset/type/fluid/FluidTicker$Accessor;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/FluidSection;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;IIILorg/joml/Vector2i;II)I";

    @WrapOperation(
            method = DISTANCE_TO_DROP,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/asset/type/fluid/FluidTicker$Accessor;getFluidSectionByBlock(III)Lcom/hypixel/hytale/server/core/universe/world/chunk/section/FluidSection;",
                            ordinal = 2),
            require = 1,
            allow = 1)
    private FluidSection refixes$belowFluidAtNeighbour(
            FluidTicker.Accessor accessor,
            int worldX,
            int y,
            int worldZ,
            Operation<FluidSection> original,
            @Local(name = "blockX") int blockX,
            @Local(name = "blockZ") int blockZ) {
        return original.call(accessor, blockX, y, blockZ);
    }

    @WrapOperation(
            method = DISTANCE_TO_DROP,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/asset/type/fluid/FluidTicker$Accessor;getBlockSectionByBlock(III)Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;",
                            ordinal = 2),
            require = 1,
            allow = 1)
    private BlockSection refixes$belowBlockAtNeighbour(
            FluidTicker.Accessor accessor,
            int worldX,
            int y,
            int worldZ,
            Operation<BlockSection> original,
            @Local(name = "blockX") int blockX,
            @Local(name = "blockZ") int blockZ) {
        return original.call(accessor, blockX, y, blockZ);
    }
}
