package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.HytaleServerConfig;
import com.hypixel.hytale.server.core.telemetry.TelemetryModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Default Telemetry to off; explicit `"Telemetry": {"Enabled": true}` re-enables.
@Mixin(TelemetryModule.class)
public class MixinTelemetryModule {

    @Redirect(
            method = "setup",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/hytale/server/core/HytaleServerConfig$Module;isEnabled(Z)Z"))
    private boolean refixes$forceDisableTelemetry(HytaleServerConfig.Module module, boolean defaultValue) {
        Boolean explicit = module.getEnabled();
        return explicit != null && explicit;
    }
}
