package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block entity sleep optimization
 * Idle block sections skip forEachTicking when a configurable tick interval hasn't elapsed.
 * Each section maintains its own tick counter, reset when it actually ticks.
 */
@Mixin(BlockSection.class)
public abstract class MixinBlockSection {

    @Shadow
    private int tickingBlocksCountCopy;

    @Unique
    private int refixes$sleepTickCounter;

    @Inject(method = "forEachTicking", at = @At("HEAD"), cancellable = true)
    private void refixes$blockEntitySleep(
            Object t, Object v, int sectionIndex, Object acceptor, CallbackInfoReturnable<Integer> cir) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.BLOCK_ENTITY_SLEEP_ENABLED.get()) {
            return;
        }
        if (tickingBlocksCountCopy == 0) {
            return;
        }
        refixes$sleepTickCounter++;
        if (refixes$sleepTickCounter < EarlyOptions.BLOCK_ENTITY_SLEEP_INTERVAL.get()) {
            cir.setReturnValue(0);
            return;
        }
        refixes$sleepTickCounter = 0;
    }
}
