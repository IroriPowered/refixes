package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.RefixesOptions;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.auth.AuthCredentialStoreProvider;
import com.hypixel.hytale.server.core.auth.IAuthCredentialStore;
import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import joptsimple.OptionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Controls auth store priority and OAuth token support for external auth.
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

    @Shadow
    private AtomicReference<IAuthCredentialStore> credentialStore;

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();
    @Inject(method = "initializeCredentialStore", at = @At("HEAD"), cancellable = true)
    private void refixes$initCredentialStoreForExternalSession(CallbackInfo ci) {
        if (getAuthMode() != ServerAuthManager.AuthMode.EXTERNAL_SESSION) {
            return; 
        }

        AuthCredentialStoreProvider provider = HytaleServer.get().getConfig().getAuthCredentialStoreProvider();
        credentialStore.set(provider.createStore());
        refixes$LOGGER.atInfo().log(
                "Auth credential store (external session): %s",
                AuthCredentialStoreProvider.CODEC.getIdFor(provider.getClass()));

        refixes$seedOAuthTokensImpl();

        ci.cancel();
    }

    @Inject(method = "initializeCredentialStore", at = @At("TAIL"))
    private void refixes$seedOAuthTokens(CallbackInfo ci) {
        refixes$seedOAuthTokensImpl();
    }

    @Unique
    private void refixes$seedOAuthTokensImpl() {
        String accessToken = refixes$readToken(RefixesOptions.OAUTH_ACCESS_TOKEN, "HYTALE_SERVER_OAUTH_ACCESS_TOKEN");
        String refreshToken =
                refixes$readToken(RefixesOptions.OAUTH_REFRESH_TOKEN, "HYTALE_SERVER_OAUTH_REFRESH_TOKEN");

        if (accessToken == null && refreshToken == null) {
            return;
        }

        Instant expiresAt = null;
        OptionSet optionSet = Options.getOptionSet();
        if (optionSet != null && optionSet.has(RefixesOptions.OAUTH_ACCESS_EXPIRES)) {
            expiresAt = Instant.ofEpochSecond(optionSet.valueOf(RefixesOptions.OAUTH_ACCESS_EXPIRES));
            refixes$LOGGER.atInfo().log("OAuth access expiry loaded from CLI");
        } else {
            String expiresStr = System.getenv("HYTALE_SERVER_OAUTH_ACCESS_EXPIRES");
            if (expiresStr != null) {
                try {
                    expiresAt = Instant.ofEpochSecond(Long.parseLong(expiresStr));
                    refixes$LOGGER.atInfo().log("OAuth access expiry loaded from environment");
                } catch (NumberFormatException e) {
                    refixes$LOGGER.atWarning().log("Invalid HYTALE_SERVER_OAUTH_ACCESS_EXPIRES value: %s", expiresStr);
                }
            }
        }

        IAuthCredentialStore store = credentialStore.get();
        if (store != null) {
            store.setTokens(new IAuthCredentialStore.OAuthTokens(accessToken, refreshToken, expiresAt));
            refixes$LOGGER.atInfo().log(
                    "Seeded credential store with OAuth tokens (access: %s, refresh: %s, expires: %s)",
                    accessToken != null ? "present" : "missing",
                    refreshToken != null ? "present" : "missing",
                    expiresAt != null ? expiresAt.toString() : "not set");
        }
    }

    @Unique
    private static String refixes$readToken(joptsimple.OptionSpec<String> cliOption, String envVar) {
        OptionSet optionSet = Options.getOptionSet();
        if (optionSet != null && optionSet.has(cliOption)) {
            String value = optionSet.valueOf(cliOption);
            refixes$LOGGER.atInfo().log("OAuth token loaded from CLI: %s", cliOption.toString());
            return value;
        }
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            refixes$LOGGER.atInfo().log("OAuth token loaded from environment: %s", envVar);
            return envValue;
        }
        return null;
    }

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
