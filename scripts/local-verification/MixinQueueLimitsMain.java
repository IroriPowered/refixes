package local.hyinit.verify.mixin;

import local.hyinit.verify.QueueLimitsRegression;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.hypixel.hytale.Main", remap = false)
public abstract class MixinQueueLimitsMain {
    @Inject(method = "main", at = @At("HEAD"), require = 1)
    private static void localVerify$queueLimits(String[] args, CallbackInfo ci) {
        QueueLimitsRegression.verify();
    }
}
