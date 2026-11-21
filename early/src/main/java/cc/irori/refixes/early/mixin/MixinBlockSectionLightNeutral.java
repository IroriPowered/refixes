package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.LightReuseState;
import cc.irori.refixes.early.util.LightNeutralBlocks;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.palette.AbstractSectionPalette;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockSection.class)
public abstract class MixinBlockSectionLightNeutral implements LightReuseState {

    @Shadow
    private AbstractSectionPalette chunkSection;

    @Shadow
    public abstract boolean hasLocalLight();

    @Shadow
    public abstract short getLocalChangeCounter();

    @Unique
    private volatile int refixes$reusableCounter;

    @Unique
    private long refixes$lightHeights;

    @Unique
    private FluidSection refixes$lightFluids;

    @Unique
    private int refixes$lightFluidVersion;

    @Unique
    private int refixes$lightAssets;

    @Inject(
            method = "set(IIII)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/palette/AbstractSectionPalette;set(II)Lcom/hypixel/hytale/server/core/universe/world/chunk/section/palette/AbstractSectionPalette$SetResult;",
                            ordinal = 0),
            require = 1,
            allow = 1)
    private void refixes$readOldBlock(
            int blockIdx,
            int blockId,
            int rotation,
            int filler,
            CallbackInfoReturnable<Boolean> cir,
            @Share("refixes$oldBlock") LocalIntRef oldBlock) {
        oldBlock.set(chunkSection.get(blockIdx));
    }

    @WrapOperation(
            method = "set(IIII)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;invalidateLocalLight()V"),
            require = 1,
            allow = 1)
    private void refixes$invalidateBlockChange(
            BlockSection section,
            Operation<Void> original,
            @Local(argsOnly = true, ordinal = 1) int blockId,
            @Share("refixes$oldBlock") LocalIntRef oldBlock) {
        short before = getLocalChangeCounter();
        boolean reusable = LightNeutralBlocks.neutral(oldBlock.get(), blockId)
                && (refixes$reusableCounter == before + 0x10000 || hasLocalLight());
        original.call(section);
        if (reusable) refixes$reusableCounter = (short) (before + 1) + 0x10000;
    }

    @Inject(method = "invalidateLocalLight", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$forgetReusableLight(CallbackInfo ci) {
        refixes$reusableCounter = 0;
    }

    @Override
    public boolean refixes$lightReusable(short changeCounter) {
        return refixes$reusableCounter == changeCounter + 0x10000;
    }

    @Override
    public boolean refixes$sameLightInputs(long heights, FluidSection fluids, int fluidVersion) {
        int assets = LightNeutralBlocks.assetGeneration();
        return refixes$lightFluids == fluids
                && refixes$lightFluidVersion == fluidVersion
                && refixes$lightHeights == heights
                && refixes$lightAssets == assets
                && (assets & 1) == 0;
    }

    @Override
    public void refixes$recordLightInputs(long heights, FluidSection fluids, int fluidVersion) {
        refixes$lightHeights = heights;
        refixes$lightFluids = fluids;
        refixes$lightFluidVersion = fluidVersion;
        refixes$lightAssets = LightNeutralBlocks.assetGeneration();
    }
}
