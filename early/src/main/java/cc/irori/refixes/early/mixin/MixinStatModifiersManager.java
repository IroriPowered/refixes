package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.server.core.entity.StatModifiersManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Reduces CPU usage by skipping recalculateEntityStatModifiers calls within a configurable tick interval.
@Mixin(StatModifiersManager.class)
public class MixinStatModifiersManager {

    @Unique
    private int refixes$recalcTickCounter;

    @Inject(method = "recalculateEntityStatModifiers", at = @At("HEAD"), cancellable = true)
    private void refixes$throttleRecalc(CallbackInfo ci) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.STAT_RECALC_THROTTLE_ENABLED.get()) {
            return;
        }
        refixes$recalcTickCounter++;
        if (refixes$recalcTickCounter < EarlyOptions.STAT_RECALC_INTERVAL.get()) {
            ci.cancel();
            return;
        }
        refixes$recalcTickCounter = 0;
    }
}
