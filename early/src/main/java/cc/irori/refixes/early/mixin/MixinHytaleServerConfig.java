package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.HytaleServerConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HytaleServerConfig.class)
public class MixinHytaleServerConfig {

    @Inject(method = "shouldSkipModValidation", at = @At("HEAD"), cancellable = true)
    private void refixes$forceSkipModValidation(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
