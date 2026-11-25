package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.worldgen.WorldGenConstants;
import com.hypixel.hytale.server.worldgen.biome.Biome;
import com.hypixel.hytale.server.worldgen.chunk.BlockPriorityChunk;
import com.hypixel.hytale.server.worldgen.chunk.ChunkGeneratorExecution;
import com.hypixel.hytale.server.worldgen.util.BlockFluidEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@IfHytale("<0.8.0-pre.1")
@Mixin(targets = "com.hypixel.hytale.server.worldgen.chunk.populator.BlockPopulator$LayerPopulator")
public abstract class MixinStaticLayerGeneration {

    /**
     * @author Refixes
     * @reason Continue with the next static layer when a layer reaches its minimum height
     */
    @Overwrite
    private static void generateStaticLayers(
            int seed, ChunkGeneratorExecution execution, int cx, int cz, int x, int z, Biome biome) {
        final var layers = biome.getLayerContainer();
        layerLoop:
        for (var layer : layers.getStaticLayers()) {
            final var entry = layer.getActiveEntry(seed, x, z);
            if (entry == null) continue;

            final int environmentId = layer.getEnvironmentId();
            final var blockEntries = entry.getBlockArray().getEntries();
            final int min = Math.max(entry.getMinInt(seed, x, z), 0);
            final int max = Math.min(entry.getMaxInt(seed, x, z), ChunkUtil.HEIGHT);

            int layerY = entry.getMaxInt(seed, x, z);
            BlockFluidEntry lastBlock = null;
            for (var blockEntry : blockEntries) {
                int repetitions = blockEntry.getRepetitions(seed, x, z);
                if (repetitions > 0) {
                    var block = blockEntry.getBlockEntry();
                    lastBlock = block;
                    for (int i = 0; i < repetitions; i++) {
                        int currentBlock = execution.getBlock(cx, --layerY, cz);
                        if (currentBlock != 0) {
                            execution.setBlock(cx, layerY, cz, BlockPriorityChunk.LAYER, block, environmentId);
                            execution.setFluid(
                                    cx, layerY, cz, BlockPriorityChunk.LAYER, block.fluidId(), environmentId);
                        }
                        if (layerY <= min) continue layerLoop;
                    }
                }
            }

            if (blockEntries.length == 0 && environmentId != WorldGenConstants.ENVIRONMENT_NOT_SET) {
                for (int y = max - 1; y >= min; --y) {
                    execution.setEnvironment(cx, y, cz, environmentId);
                }
            }

            if (lastBlock == null) continue;
            while (layerY > min) {
                int currentBlock = execution.getBlock(cx, --layerY, cz);
                if (currentBlock != 0) {
                    execution.setBlock(cx, layerY, cz, BlockPriorityChunk.LAYER, lastBlock);
                    execution.setFluid(cx, layerY, cz, BlockPriorityChunk.LAYER, lastBlock.fluidId());
                }
            }
        }
    }
}
