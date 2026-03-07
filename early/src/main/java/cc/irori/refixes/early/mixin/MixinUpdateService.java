package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import com.hypixel.hytale.server.core.update.UpdateService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Fall back to session/identity token when OAuth is unavailable
@Mixin(UpdateService.class)
public class MixinUpdateService {

    @Redirect(
            method = {"checkForUpdate", "performDownload"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/auth/ServerAuthManager;getOAuthAccessToken()Ljava/lang/String;"))
    private String refixes$fallbackToken(ServerAuthManager authManager) {
        String token = authManager.getOAuthAccessToken();
        if (token != null) {
            return token;
        }
        token = authManager.getSessionToken();
        if (token != null) {
            return token;
        }
        return authManager.getIdentityToken();
    }
}
