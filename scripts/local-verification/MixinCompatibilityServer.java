package local.hyinit.verify.mixin;

import com.hypixel.hytale.server.core.HytaleServer;
import local.hyinit.verify.CompatibilityProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HytaleServer.class)
public abstract class MixinCompatibilityServer {
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void localVerify$observeBoot(CallbackInfo ci) {
        CompatibilityProbe.observeBoot((HytaleServer) (Object) this);
    }
}
