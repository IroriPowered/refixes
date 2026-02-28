package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightDataBuilder;
import com.hypixel.hytale.server.core.universe.world.lighting.FloodLightCalculation;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import java.util.BitSet;
import java.util.function.IntBinaryOperator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Skip empty sections during light propagation as there is nothing to propagate
@Mixin(FloodLightCalculation.class)
public class MixinFloodLightCalculation {

    @Inject(method = "propagateSide", at = @At("HEAD"), cancellable = true)
    private void refixes$skipEmptySide(
            BitSet bitSetQueue,
            BlockSection fromSection,
            BlockSection toSection,
            ChunkLightDataBuilder toLight,
            IntBinaryOperator fromIndex,
            IntBinaryOperator toIndex,
            CallbackInfo ci) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.SKIP_EMPTY_LIGHT_SECTIONS.get()) {
            return;
        }
        if (fromSection != null && fromSection.isSolidAir() && fromSection.getLocalLight() == ChunkLightData.EMPTY) {
            ci.cancel();
        }
    }

    @Inject(method = "propagateEdge", at = @At("HEAD"), cancellable = true)
    private void refixes$skipEmptyEdge(
            BitSet bitSetQueue,
            BlockSection fromSection,
            BlockSection toSection,
            ChunkLightDataBuilder toLight,
            Int2IntFunction fromIndex,
            Int2IntFunction toIndex,
            CallbackInfo ci) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.SKIP_EMPTY_LIGHT_SECTIONS.get()) {
            return;
        }
        if (fromSection != null && fromSection.isSolidAir() && fromSection.getLocalLight() == ChunkLightData.EMPTY) {
            ci.cancel();
        }
    }

    @Inject(method = "propagateCorner", at = @At("HEAD"), cancellable = true)
    private void refixes$skipEmptyCorner(
            BitSet bitSetQueue,
            BlockSection fromSection,
            BlockSection toSection,
            ChunkLightDataBuilder toLight,
            int fromBlockIndex,
            int toBlockIndex,
            CallbackInfo ci) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.SKIP_EMPTY_LIGHT_SECTIONS.get()) {
            return;
        }
        if (fromSection != null && fromSection.isSolidAir() && fromSection.getLocalLight() == ChunkLightData.EMPTY) {
            ci.cancel();
        }
    }
}
