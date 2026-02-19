package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Guards against the AssertionError in debugAssertInTickingThread during shutdown.
@Mixin(TickingThread.class)
public class MixinTickingThreadAssert {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Shadow
    private Thread thread;

    @Inject(method = "debugAssertInTickingThread", at = @At("HEAD"), cancellable = true)
    private void refixes$skipAssertDuringShutdown(CallbackInfo ci) {
        if (thread != null && !thread.isAlive()) {
            refixes$LOGGER.atFine().log(
                    "TickingThread#debugAssertInTickingThread(): Skipped assertion (thread no longer alive during shutdown)");
            ci.cancel();
        }
    }
}
