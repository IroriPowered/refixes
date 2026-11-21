package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.FluidLightVersion;
import cc.irori.refixes.early.duck.LightReuseState;
import cc.irori.refixes.early.util.PatchCounters;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightDataBuilder;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.lighting.FloodLightCalculation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FloodLightCalculation.class)
public class MixinFloodLightNeutral {

    @Shadow
    @Final
    private ChunkLightDataBuilder builder;

    @WrapOperation(
            method = "updateLocalLight",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/lighting/FloodLightCalculation;floodEmptyChunkSection(Lcom/hypixel/hytale/server/core/universe/world/chunk/heightmap/HeightmapColumn;SI)Lcom/hypixel/hytale/server/core/universe/world/chunk/section/ChunkLightDataBuilder;"),
            require = 1,
            allow = 1)
    private ChunkLightDataBuilder refixes$reuseEmptyLight(
            FloodLightCalculation calculation,
            HeightmapColumn heightmap,
            short changeCounter,
            int chunkY,
            Operation<ChunkLightDataBuilder> original,
            @Local(argsOnly = true) BlockSection blocks,
            @Local(argsOnly = true) FluidSection fluids) {
        ChunkLightDataBuilder reused = refixes$reuse(heightmap, blocks, fluids, changeCounter, chunkY);
        return reused != null ? reused : original.call(calculation, heightmap, changeCounter, chunkY);
    }

    @WrapOperation(
            method = "updateLocalLight",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/lighting/FloodLightCalculation;floodChunkSection(Lcom/hypixel/hytale/server/core/universe/world/chunk/heightmap/HeightmapColumn;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/FluidSection;I)Lcom/hypixel/hytale/server/core/universe/world/chunk/section/ChunkLightDataBuilder;"),
            require = 1,
            allow = 1)
    private ChunkLightDataBuilder refixes$reuseLight(
            FloodLightCalculation calculation,
            HeightmapColumn heightmap,
            BlockSection blocks,
            FluidSection fluids,
            int chunkY,
            Operation<ChunkLightDataBuilder> original) {
        ChunkLightDataBuilder reused = refixes$reuse(heightmap, blocks, fluids, blocks.getLocalChangeCounter(), chunkY);
        return reused != null ? reused : original.call(calculation, heightmap, blocks, fluids, chunkY);
    }

    @Unique
    private ChunkLightDataBuilder refixes$reuse(
            HeightmapColumn heightmap, BlockSection blocks, FluidSection fluids, short changeCounter, int chunkY) {
        LightReuseState state = (LightReuseState) blocks;
        boolean reusable = state.refixes$lightReusable(changeCounter);
        long heights = refixes$heights(heightmap, chunkY);
        int fluidVersion = ((FluidLightVersion) fluids).refixes$fluidVersion();
        if (PatchCounters.ENABLED) PatchCounters.LIGHT_NEUTRAL_BLOCKS.countReached();
        if (reusable && state.refixes$sameLightInputs(heights, fluids, fluidVersion)) {
            if (PatchCounters.ENABLED) PatchCounters.LIGHT_NEUTRAL_BLOCKS.countEffect();
            return builder.reset(blocks.getLocalLight(), changeCounter);
        }
        state.refixes$recordLightInputs(heights, fluids, fluidVersion);
        return null;
    }

    @Unique
    private static long refixes$heights(HeightmapColumn heightmap, int chunkY) {
        int low = chunkY * 32 - 1;
        int high = low + 33;
        long hash = 0xcbf29ce484222325L;
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                int height = Math.min(Math.max(heightmap.getHeight(x, z), low), high);
                hash = (hash ^ height) * 0x100000001b3L;
            }
        }
        return hash;
    }
}
