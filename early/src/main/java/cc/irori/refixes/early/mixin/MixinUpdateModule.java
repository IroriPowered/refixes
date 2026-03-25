package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import com.hypixel.hytale.server.core.update.UpdateModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// UpdateModule.performUpdateCheck() now support external auth
@Mixin(UpdateModule.class)
public class MixinUpdateModule {

    @Redirect(
            method = "performUpdateCheck",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/hytale/server/core/auth/ServerAuthManager;hasSessionToken()Z"))
    private boolean refixes$broaderAuthCheck(ServerAuthManager instance) {
        return instance.hasSessionToken() || instance.hasIdentityToken();
    }
}
