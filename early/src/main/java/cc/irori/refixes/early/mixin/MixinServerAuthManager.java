package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Controls auth store priority and OAuth token fallback for external auth.
@Mixin(ServerAuthManager.class)
public abstract class MixinServerAuthManager {

    @Shadow
    public abstract ServerAuthManager.AuthMode getAuthMode();

    @Shadow
    public abstract boolean hasSessionToken();

    @Shadow
    public abstract boolean hasIdentityToken();

    @Shadow
    public abstract String getSessionToken();

    @Shadow
    public abstract String getIdentityToken();

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Inject(method = "initializeCredentialStore", at = @At("HEAD"), cancellable = true)
    private void refixes$skipInitIfExternalSession(CallbackInfo ci) {
        if (getAuthMode() != ServerAuthManager.AuthMode.EXTERNAL_SESSION) {
            return;
        }

        boolean preferExternal = !EarlyOptions.isAvailable()
                || !EarlyOptions.PREFER_EXTERNAL_AUTH.isSet()
                || EarlyOptions.PREFER_EXTERNAL_AUTH.get();
        if (!preferExternal) {
            return;
        }

        boolean hasTokens = hasSessionToken() || hasIdentityToken();
        if (hasTokens) {
            refixes$LOGGER.atInfo().log(
                    "Skipping initializeCredentialStore: external session already has valid tokens (PreferExternalAuth=true)");
            ci.cancel();
        } else {
            refixes$LOGGER.atInfo().log("External session has no tokens, falling back to internal auth store");
        }
    }

    // When OAuth token is unavailable (external auth), fall back to session/identity token
    @Inject(method = "getOAuthAccessToken", at = @At("RETURN"), cancellable = true)
    private void refixes$oauthFallback(CallbackInfoReturnable<String> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }
        String token = getSessionToken();
        if (token != null) {
            refixes$LOGGER.atInfo().log("OAuth token unavailable, using session token as fallback");
            cir.setReturnValue(token);
            return;
        }
        token = getIdentityToken();
        if (token != null) {
            refixes$LOGGER.atInfo().log("OAuth token unavailable, using identity token as fallback");
            cir.setReturnValue(token);
        }
    }
}
