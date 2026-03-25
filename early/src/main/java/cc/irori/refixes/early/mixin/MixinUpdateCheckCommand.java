package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.auth.ServerAuthManager;
import com.hypixel.hytale.server.core.update.command.UpdateCheckCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// accept identity tokens in /update check
@Mixin(UpdateCheckCommand.class)
public class MixinUpdateCheckCommand {

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
