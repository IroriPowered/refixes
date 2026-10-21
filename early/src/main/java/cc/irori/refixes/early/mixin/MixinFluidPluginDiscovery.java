package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.FluidSectionPaletteAccess;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.assetstore.map.IndexedLookupTableAssetMap;
import com.hypixel.hytale.builtin.fluid.FluidPlugin;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.fluid.FluidTicker;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.palette.AbstractSectionPalette;
import com.hypixel.hytale.server.core.universe.world.chunk.section.palette.HalfByteSectionPalette;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.logging.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidPlugin.class)
public class MixinFluidPluginDiscovery {

    @Shadow
    @Final
    private static HytaleLogger LOGGER;

    @Inject(method = "discoverFluids", at = @At("HEAD"), cancellable = true)
    private static void refixes$discoverFluidCells(
            FluidSection fluidSection,
            BlockSection blockSection,
            FluidSection belowFluidSection,
            BlockSection belowBlockSection,
            boolean bottomIsWorldFloor,
            CallbackInfo ci) {
        AbstractSectionPalette palette = ((FluidSectionPaletteAccess) fluidSection).refixes$getTypePalette();
        if (!(palette instanceof HalfByteSectionPalette)) {
            return;
        }
        ci.cancel();
        IntArrayList fluidIds = new IntArrayList(16);
        palette.forEachValue(id -> {
            if (id != Fluid.EMPTY_ID) {
                fluidIds.add(id);
            }
        });
        if (fluidIds.isEmpty()) {
            return;
        }
        IndexedLookupTableAssetMap<String, Fluid> fluidMap = Fluid.getAssetMap();
        BlockTypeAssetMap<String, BlockType> blockMap = BlockType.getAssetMap();
        int sectionY = fluidSection.getY();
        palette.find(fluidIds, (int idx) -> {
            int fluidId = fluidSection.getFluidId(idx);
            if (fluidId != Fluid.EMPTY_ID) {
                refixes$discoverCell(
                        fluidMap,
                        blockMap,
                        fluidSection,
                        blockSection,
                        belowFluidSection,
                        belowBlockSection,
                        bottomIsWorldFloor,
                        sectionY,
                        idx,
                        fluidId);
            }
        });
    }

    @Unique
    private static void refixes$discoverCell(
            IndexedLookupTableAssetMap<String, Fluid> fluidMap,
            BlockTypeAssetMap<String, BlockType> blockMap,
            FluidSection fluidSection,
            BlockSection blockSection,
            FluidSection belowFluidSection,
            BlockSection belowBlockSection,
            boolean bottomIsWorldFloor,
            int sectionY,
            int idx,
            int fluidId) {
        Fluid fluidType = fluidMap.getAsset(fluidId);
        if (fluidType == null) {
            LOGGER.at(Level.WARNING).log(
                    "Invalid fluid found in chunk section: %d, %d %d with id %d",
                    fluidSection.getX(), sectionY, fluidSection.getZ(), fluidId);
            fluidSection.setFluid(idx, 0, (byte) 0);
            return;
        }
        if (FluidTicker.isSolid(blockMap.getAsset(blockSection.get(idx)))) {
            fluidSection.setFluid(idx, 0, (byte) 0);
            return;
        }
        if (!fluidType.getTicker().canDemote()) {
            int x = ChunkUtil.minBlock(fluidSection.getX()) + ChunkUtil.xFromIndex(idx);
            int y = ChunkUtil.minBlock(sectionY) + ChunkUtil.yFromIndex(idx);
            int z = ChunkUtil.minBlock(fluidSection.getZ()) + ChunkUtil.zFromIndex(idx);
            boolean canSpread = ChunkUtil.isBorderBlockGlobal(x, z)
                    || fluidSection.getFluidId(x - 1, y, z) != fluidId
                            && !FluidTicker.isSolid(blockMap.getAsset(blockSection.get(x - 1, y, z)))
                    || fluidSection.getFluidId(x + 1, y, z) != fluidId
                            && !FluidTicker.isSolid(blockMap.getAsset(blockSection.get(x + 1, y, z)))
                    || fluidSection.getFluidId(x, y, z - 1) != fluidId
                            && !FluidTicker.isSolid(blockMap.getAsset(blockSection.get(x, y, z - 1)))
                    || fluidSection.getFluidId(x, y, z + 1) != fluidId
                            && !FluidTicker.isSolid(blockMap.getAsset(blockSection.get(x, y, z + 1)));
            if (!canSpread) {
                if (ChunkUtil.chunkCoordinate(y - 1) == sectionY) {
                    canSpread = fluidSection.getFluidId(x, y - 1, z) != fluidId
                            && !FluidTicker.isSolid(blockMap.getAsset(blockSection.get(x, y - 1, z)));
                } else if (belowBlockSection != null) {
                    int belowFluidId = belowFluidSection == null ? 0 : belowFluidSection.getFluidId(x, y - 1, z);
                    canSpread = belowFluidId != fluidId
                            && !FluidTicker.isSolid(blockMap.getAsset(belowBlockSection.get(x, y - 1, z)));
                } else if (!bottomIsWorldFloor) {
                    canSpread = true;
                }
            }
            if (!canSpread) {
                blockSection.setTicking(idx, false);
                return;
            }
        }
        blockSection.setTicking(idx, true);
    }
}
