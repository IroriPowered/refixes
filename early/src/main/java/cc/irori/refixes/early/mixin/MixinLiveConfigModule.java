package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.liveconfig.LiveConfigModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Skip LiveConfig refresh; flags fall back to local defaults.
@Mixin(LiveConfigModule.class)
public class MixinLiveConfigModule {

    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void refixes$skipLiveConfigRefresh(CallbackInfo ci) {
        ci.cancel();
    }
}
