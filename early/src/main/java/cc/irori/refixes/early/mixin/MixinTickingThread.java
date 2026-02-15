package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TickingThread.class)
public class MixinTickingThread {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Redirect(
            method = "stop",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Thread;stop()V"
            )
    )
    private void refixes$wrapStop(Thread instance) {
        try {
            instance.stop();
        } catch (UnsupportedOperationException e) {
            refixes$LOGGER.atWarning().log("TickingThread#stop(): Replaced Thread.stop() with Thread.interrupt() (Java 21+)");
            instance.interrupt();
        }
    }
}
