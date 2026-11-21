package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.BlockSectionStoredLight;
import cc.irori.refixes.early.util.LightComparison;
import cc.irori.refixes.early.util.PatchCounters;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightDataBuilder;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.lighting.FloodLightCalculation;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FloodLightCalculation.class)
public class MixinFloodLightSaveMarks {

    @WrapOperation(
            method = "updateLocalLight",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;setLocalLight(Lcom/hypixel/hytale/server/core/universe/world/chunk/section/ChunkLightDataBuilder;)V"),
            require = 1,
            allow = 1)
    private void refixes$compareLocalLight(
            BlockSection blocks,
            ChunkLightDataBuilder light,
            Operation<Void> original,
            @Share("refixes$changed") LocalBooleanRef changed) {
        changed.set(!LightComparison.same(blocks.getLocalLight(), light));
        original.call(blocks, light);
    }

    @WrapOperation(
            method = "updateLocalLight",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;setSkyLitStamp(J)V"),
            require = 1,
            allow = 1)
    private void refixes$compareSkyStamp(
            BlockSection blocks,
            long stamp,
            Operation<Void> original,
            @Share("refixes$changed") LocalBooleanRef changed) {
        if (blocks.getSkyLitStamp() != stamp) changed.set(true);
        original.call(blocks, stamp);
    }

    @WrapWithCondition(
            method = "updateLocalLight",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/ChunkSection;markNeedsSaving()V"),
            require = 1,
            allow = 1)
    private boolean refixes$markChangedLocalLight(
            ChunkSection section,
            @Local(argsOnly = true) BlockSection blocks,
            @Share("refixes$changed") LocalBooleanRef changed) {
        return refixes$counted(changed.get() || refixes$repairsStoredLight(section, blocks));
    }

    @WrapOperation(
            method = "updateGlobalLight",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;setGlobalLight(Lcom/hypixel/hytale/server/core/universe/world/chunk/section/ChunkLightDataBuilder;)V"),
            require = 1,
            allow = 1)
    private void refixes$compareGlobalLight(
            BlockSection blocks,
            ChunkLightDataBuilder light,
            Operation<Void> original,
            @Share("refixes$changed") LocalBooleanRef changed) {
        changed.set(!LightComparison.same(blocks.getGlobalLight(), light));
        original.call(blocks, light);
    }

    @WrapWithCondition(
            method = "updateGlobalLight",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/ChunkSection;markNeedsSaving()V"),
            require = 1,
            allow = 1)
    private boolean refixes$markChangedGlobalLight(
            ChunkSection section,
            @Local(argsOnly = true) BlockSection blocks,
            @Share("refixes$changed") LocalBooleanRef changed) {
        return refixes$counted(changed.get() || refixes$repairsStoredLight(section, blocks));
    }

    @Unique
    private static boolean refixes$repairsStoredLight(ChunkSection section, BlockSection blocks) {
        return ((BlockSectionStoredLight) blocks).refixes$storedUnlit() && !section.needsSaving();
    }

    @Unique
    private static boolean refixes$counted(boolean changed) {
        if (PatchCounters.ENABLED) {
            PatchCounters.LIGHT_SAVE_MARKS.countReached();
            if (!changed) PatchCounters.LIGHT_SAVE_MARKS.countEffect();
        }
        return changed;
    }
}
