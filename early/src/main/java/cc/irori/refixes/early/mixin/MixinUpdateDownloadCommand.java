package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import com.hypixel.hytale.server.core.update.command.UpdateDownloadCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// accept identity tokens in /update download
@Mixin(UpdateDownloadCommand.class)
public class MixinUpdateDownloadCommand {

    @Redirect(
            method = "executeAsync",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/hytale/server/core/auth/ServerAuthManager;hasSessionToken()Z"))
    private boolean refixes$broaderAuthCheck(ServerAuthManager authManager) {
        return authManager.hasSessionToken() || authManager.hasIdentityToken();
    }
}
