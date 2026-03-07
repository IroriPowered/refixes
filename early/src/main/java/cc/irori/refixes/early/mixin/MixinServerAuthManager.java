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

// Controls auth store priority when both external and internal stores are available.
@Mixin(ServerAuthManager.class)
public abstract class MixinServerAuthManager {

    @Shadow
    public abstract ServerAuthManager.AuthMode getAuthMode();

    @Shadow
    public abstract boolean hasSessionToken();

    @Shadow
    public abstract boolean hasIdentityToken();

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
}
