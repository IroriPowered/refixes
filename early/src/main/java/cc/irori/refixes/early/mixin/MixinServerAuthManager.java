package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.RefixesOptions;
import cc.irori.refixes.early.util.Logs;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.auth.EncryptedAuthCredentialStoreProvider;
import com.hypixel.hytale.server.core.auth.IAuthCredentialStore;
import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import com.hypixel.hytale.server.core.auth.SessionServiceClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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

    @Shadow
    private Map<UUID, SessionServiceClient.GameProfile> availableProfiles;

    @Shadow
    private SessionServiceClient sessionServiceClient;

    @Shadow
    private void setExpiryAndScheduleRefresh(Instant expiry) {}

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final Gson refixes$GSON = new Gson();

    @Unique
    private static final String refixes$AUTH_FILE = ".hytale-auth.json";

    @Inject(method = "initializeCredentialStore", at = @At("HEAD"), cancellable = true)
    private void refixes$initCredentialStoreForExternalSession(CallbackInfo ci) {
        if (getAuthMode() != ServerAuthManager.AuthMode.EXTERNAL_SESSION) {
            return;
        }

        // Force encrypted storage for external session to persist tokens
        EncryptedAuthCredentialStoreProvider encryptedProvider = new EncryptedAuthCredentialStoreProvider();
        credentialStore.set(encryptedProvider.createStore());
        refixes$LOGGER.atInfo().log(
                "Auth credential store (external session): Encrypted (forced for token persistence)");

        IAuthCredentialStore store = credentialStore.get();

        String envAccessToken =
                refixes$readToken(RefixesOptions.OAUTH_ACCESS_TOKEN, "HYTALE_SERVER_OAUTH_ACCESS_TOKEN");
        String envRefreshToken =
                refixes$readToken(RefixesOptions.OAUTH_REFRESH_TOKEN, "HYTALE_SERVER_OAUTH_REFRESH_TOKEN");

        if (envAccessToken != null || envRefreshToken != null) {
            refixes$LOGGER.atInfo().log("Loading fresh tokens from environment variables");
            Instant seededExpiry = refixes$seedOAuthTokensImpl();
            if (seededExpiry != null) {
                setExpiryAndScheduleRefresh(seededExpiry);
                refixes$LOGGER.atInfo().log("Token refresh scheduler started (expires: %s)", seededExpiry);
            }
        } else {
            IAuthCredentialStore.OAuthTokens existingTokens = store != null ? store.getTokens() : null;
            if (existingTokens != null
                    && (existingTokens.accessToken() != null || existingTokens.refreshToken() != null)) {
                Instant expiresAt = existingTokens.accessTokenExpiresAt();
                boolean accessValid =
                        expiresAt != null && expiresAt.isAfter(Instant.now().plusSeconds(60));

                if (accessValid) {
                    refixes$LOGGER.atInfo().log(
                            "Using persisted tokens from credential store (expires: %s)", expiresAt);
                    setExpiryAndScheduleRefresh(expiresAt);
                } else if (existingTokens.refreshToken() != null) {
                    refixes$LOGGER.atInfo().log(
                            "Access token expired, but refresh token available. Attempting refresh");
                    if (expiresAt != null) {
                        setExpiryAndScheduleRefresh(expiresAt);
                    }
                } else {
                    refixes$LOGGER.atWarning().log("Stored tokens expired and no refresh token available");
                }
            } else {
                refixes$LOGGER.atWarning().log("No tokens available from environment or storage");
            }
        }

        String profileUuid = refixes$readToken(RefixesOptions.PROFILE_UUID, "HYTALE_PROFILE_UUID");
        if (profileUuid != null && !profileUuid.isEmpty()) {
            UUID uuid = UUID.fromString(profileUuid);
            if (store != null && store.getProfile() == null) {
                store.setProfile(uuid);
            }
            String profileName = refixes$readToken(RefixesOptions.PROFILE_NAME, "HYTALE_PROFILE_NAME");
            SessionServiceClient.GameProfile profile = new SessionServiceClient.GameProfile();
            profile.uuid = uuid;
            profile.username = profileName != null ? profileName : uuid.toString();
            availableProfiles.put(uuid, profile);
            refixes$LOGGER.atInfo().log("Profile set from environment: %s (%s)", profile.username, profileUuid);
        }

        ci.cancel();
    }

    @Inject(method = "initializeCredentialStore", at = @At("TAIL"))
    private void refixes$seedOAuthTokens(CallbackInfo ci) {
        refixes$seedOAuthTokensImpl();
    }

    @Shadow
    private AtomicReference<SessionServiceClient.GameSessionResponse> gameSession;

    @Shadow
    private SessionServiceClient.GameSessionResponse createGameSession(UUID profileUuid) {
        return null;
    }

    @Shadow
    private Instant getEffectiveExpiry(SessionServiceClient.GameSessionResponse response) {
        return null;
    }

    @Inject(method = "refreshGameSessionViaOAuth", at = @At("HEAD"), cancellable = true)
    private void refixes$allowExternalSessionRefresh(CallbackInfoReturnable<Boolean> cir) {
        if (getAuthMode() != ServerAuthManager.AuthMode.EXTERNAL_SESSION) {
            return;
        }

        UUID currentProfile = credentialStore.get().getProfile();
        if (currentProfile == null) {
            refixes$LOGGER.atWarning().log("No current profile, cannot refresh game session via OAuth");
            cir.setReturnValue(false);
            return;
        }

        // Retry session creation with exponential backoff to avoid unnecessary OAuth fallback
        int maxRetries = 3;
        long baseDelay = 1000;
        SessionServiceClient.GameSessionResponse newSession = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                newSession = createGameSession(currentProfile);
                if (newSession != null) {
                    break;
                }
            } catch (Exception e) {
                refixes$LOGGER.atWarning().log(
                        "Game session creation attempt %d/%d failed: %s", attempt, maxRetries, e.getMessage());
            }

            if (attempt < maxRetries) {
                long delay = baseDelay * (1L << (attempt - 1));
                refixes$LOGGER.atInfo().log("Retrying in %dms...", delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (newSession == null) {
            refixes$LOGGER.atWarning().log(
                    "Failed to create new game session after %d attempts, falling back to OAuth refresh", maxRetries);
            return;
        }

        gameSession.set(newSession);
        Instant effectiveExpiry = getEffectiveExpiry(newSession);
        if (effectiveExpiry != null) {
            setExpiryAndScheduleRefresh(effectiveExpiry);
        }

        refixes$LOGGER.atInfo().log("Game session refreshed via OAuth for external session");
        cir.setReturnValue(true);
    }

    @Inject(method = "refreshGameSessionViaOAuth", at = @At("RETURN"))
    private void refixes$syncTokensAfterOAuthRefresh(CallbackInfoReturnable<Boolean> cir) {
        if (getAuthMode() == ServerAuthManager.AuthMode.EXTERNAL_SESSION && cir.getReturnValue() == Boolean.TRUE) {
            refixes$syncTokensToEntrypoint();
        }
    }

    @Inject(method = "refreshOAuthTokens", at = @At("RETURN"))
    private void refixes$syncTokensAfterRefresh(CallbackInfoReturnable<Boolean> cir) {
        if (getAuthMode() == ServerAuthManager.AuthMode.EXTERNAL_SESSION && cir.getReturnValue() == Boolean.TRUE) {
            refixes$syncTokensToEntrypoint();
        }
    }

    @Unique
    private Instant refixes$seedOAuthTokensImpl() {
        String accessToken = refixes$readToken(RefixesOptions.OAUTH_ACCESS_TOKEN, "HYTALE_SERVER_OAUTH_ACCESS_TOKEN");
        String refreshToken =
                refixes$readToken(RefixesOptions.OAUTH_REFRESH_TOKEN, "HYTALE_SERVER_OAUTH_REFRESH_TOKEN");

        if (accessToken == null && refreshToken == null) {
            return null;
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

        return expiresAt;
    }

    @Unique
    private static String refixes$readToken(joptsimple.OptionSpec<String> cliOption, String envVar) {
        OptionSet optionSet = Options.getOptionSet();
        if (optionSet != null && optionSet.has(cliOption)) {
            String value = optionSet.valueOf(cliOption);
            refixes$LOGGER.atInfo().log("Token loaded from CLI: %s", cliOption.toString());
            return value;
        }
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            refixes$LOGGER.atInfo().log("Token loaded from environment: %s", envVar);
            return envValue;
        }
        return null;
    }

    @Unique
    private void refixes$syncTokensToEntrypoint() {
        try {
            IAuthCredentialStore store = credentialStore.get();
            if (store == null) {
                return;
            }

            IAuthCredentialStore.OAuthTokens tokens = store.getTokens();
            if (tokens == null) {
                return;
            }

            Path authFile = Paths.get("..", refixes$AUTH_FILE);
            if (!Files.exists(authFile)) {
                refixes$LOGGER.atWarning().log("Auth file not found: %s", authFile);
                return;
            }

            String content = Files.readString(authFile);
            JsonObject json = refixes$GSON.fromJson(content, JsonObject.class);

            if (tokens.accessToken() != null) {
                json.addProperty("access_token", tokens.accessToken());
            }
            if (tokens.refreshToken() != null) {
                json.addProperty("refresh_token", tokens.refreshToken());
            }
            if (tokens.accessTokenExpiresAt() != null) {
                json.addProperty("access_expires", tokens.accessTokenExpiresAt().getEpochSecond());
            }

            String sessionToken = getSessionToken();
            if (sessionToken != null && !sessionToken.isEmpty()) {
                json.addProperty("session_token", sessionToken);
            }

            String identityToken = getIdentityToken();
            if (identityToken != null && !identityToken.isEmpty()) {
                json.addProperty("identity_token", identityToken);
            }

            SessionServiceClient.GameSessionResponse session = gameSession.get();
            if (session != null && session.expiresAt != null) {
                try {
                    Instant expiresAt = Instant.parse(session.expiresAt);
                    json.addProperty("session_expires", expiresAt.getEpochSecond());
                } catch (Exception e) {
                    refixes$LOGGER.atWarning().log("Failed to parse session expiry: %s", e.getMessage());
                }
            }

            UUID profileUuid = store.getProfile();
            if (profileUuid != null) {
                json.addProperty("profile_uuid", profileUuid.toString());
                SessionServiceClient.GameProfile profile = availableProfiles.get(profileUuid);
                if (profile != null && profile.username != null) {
                    json.addProperty("profile_name", profile.username);
                }
            }

            Files.writeString(authFile, refixes$GSON.toJson(json));
            refixes$LOGGER.atInfo().log("Synced OAuth tokens to external auth file");
        } catch (Exception e) {
            refixes$LOGGER.atWarning().log("Failed to sync tokens to external auth file: %s", e.getMessage());
        }
    }

    @Inject(method = "shutdown", at = @At("TAIL"))
    private void refixes$terminateExternalSession(CallbackInfo ci) {
        String currentSessionToken = getSessionToken();
        if (currentSessionToken != null && !currentSessionToken.isEmpty()) {
            if (sessionServiceClient == null) {
                sessionServiceClient = new SessionServiceClient("https://sessions.hytale.com");
            }
            sessionServiceClient.terminateSession(currentSessionToken);
            refixes$LOGGER.atInfo().log("Terminated session on shutdown");
        }
    }
}
