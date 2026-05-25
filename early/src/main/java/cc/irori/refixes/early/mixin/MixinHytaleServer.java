package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.event.events.BootEvent;
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
    private static final String MAIN_PLUGIN_CLASS = "cc.irori.refixes.Refixes";

    @Inject(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lio/netty/handler/codec/quic/Quic;ensureAvailability()V"))
    private void refixes$setupBootEvent(CallbackInfo ci) {
        HytaleServer server = (HytaleServer) (Object) this;
        server.getEventBus().register(BootEvent.class, event -> {
            if (!refixes$mainPluginPresent()) {
                refixes$LOGGER.atWarning().log(
                        "Refixes Main Plugin is not installed! Some runtime fixes will not be applied.");
            }
        });
    }

    @Unique
    private static boolean refixes$mainPluginPresent() {
        try {
            Class.forName(MAIN_PLUGIN_CLASS, false, MixinHytaleServer.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
