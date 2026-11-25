package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HytaleServer.class)
public class MixinHytaleServer {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final PluginIdentifier MAIN_PLUGIN_ID = new PluginIdentifier("IroriPowered", "Refixes");

    @IfHytale("<0.8.0-pre.1")
    @Inject(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lio/netty/handler/codec/quic/Quic;ensureAvailability()V"))
    private void refixes$setupBootEventBeforeQuic(CallbackInfo ci) {
        refixes$setupBootEvent();
    }

    @IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
    @Inject(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/HytaleServer;boot()V"),
            require = 1,
            allow = 1)
    private void refixes$setupBootEventBeforeBoot(CallbackInfo ci) {
        refixes$setupBootEvent();
    }

    @Unique
    private void refixes$setupBootEvent() {
        HytaleServer server = (HytaleServer) (Object) this;
        server.getEventBus().register(BootEvent.class, event -> {
            PluginBase plugin = PluginManager.get().getPlugin(MAIN_PLUGIN_ID);
            if (plugin == null || !plugin.isEnabled()) {
                refixes$LOGGER.atWarning().log(
                        "Refixes Main Plugin is not installed or enabled! Some runtime fixes will not be applied.");
            }
        });
    }
}
