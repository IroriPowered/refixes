package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import java.util.concurrent.locks.LockSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Replaces deprecated Thread.stop() with Thread.interrupt() on Java 21+

@Mixin(TickingThread.class)
public class MixinTickingThread {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final boolean refixes$SLEEP_OPT_ENABLED =
            Boolean.parseBoolean(System.getProperty("refixes.tickSleepOptimization", "true"));

    @Unique
    private static final long refixes$SPIN_THRESHOLD_NANOS =
            Long.getLong("refixes.tickSpinThresholdNanos", 500_000L); // 0.5 ms

    @Shadow
    private int tickStepNanos;

    static {
        if (refixes$SLEEP_OPT_ENABLED) {
            TickingThread.SLEEP_OFFSET = 1_000_000L; // 1 ms
            refixes$LOGGER.atInfo().log(
                    "TickingThread tick sleep optimization: ENABLED (SLEEP_OFFSET=1ms, spinThreshold=%dµs)",
                    refixes$SPIN_THRESHOLD_NANOS / 1000);
        } else {
            refixes$LOGGER.atInfo().log("TickingThread tick sleep optimization: DISABLED");
        }
    }

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
        if (!refixes$SLEEP_OPT_ENABLED) {
            Thread.onSpinWait();
            return;
        }

        Thread.onSpinWait();
        // Park for 100µs, loop will re-check and spin-wait naturally as it approaches the deadline
        LockSupport.parkNanos(100_000L);
    }
}
