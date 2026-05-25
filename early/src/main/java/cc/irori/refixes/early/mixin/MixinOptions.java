package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.RefixesOptions;
import com.hypixel.hytale.server.core.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public class MixinOptions {

    @Inject(method = "parse", at = @At("HEAD"))
    private static void refixes$registerOptions(String[] args, CallbackInfoReturnable<Boolean> cir) {
        RefixesOptions.init();
    }
}
