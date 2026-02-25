package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import java.util.concurrent.locks.LockSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Replaces deprecated Thread.stop() with Thread.interrupt() on Java 21+

@Mixin(TickingThread.class)
public class MixinTickingThread {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Redirect(method = "stop", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;stop()V"))
    private void refixes$wrapStop(Thread instance) {
        try {
            instance.stop();
        } catch (UnsupportedOperationException e) {
            refixes$LOGGER.atWarning().log(
                    "TickingThread#stop(): Replaced Thread.stop() with Thread.interrupt() (Java 21+)");
            instance.interrupt();
        }
    }

    // Replaces the pure spin-wait in the tick loop with a hybrid spin approach
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;onSpinWait()V"))
    private void refixes$hybridWait() {
        Thread.onSpinWait();
        // Park for 100ns, loop will re-check and spin-wait naturally as it approaches the deadline
        LockSupport.parkNanos(100_000L);
    }
}
