package local.hyinit.verify.mixin;

import com.hypixel.hytale.server.core.HytaleServer;
import local.hyinit.verify.CompatibilityProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HytaleServer.class)
public abstract class MixinCompatibilitySetup {
    @Inject(
            method = "boot",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/auth/ServerAuthManager;initializeCredentialStore()V",
                            shift = At.Shift.AFTER),
            require = 1)
    private void localVerify$afterSetup(CallbackInfo ci) {
        CompatibilityProbe.verifyAfterSetup((HytaleServer) (Object) this);
    }
}
