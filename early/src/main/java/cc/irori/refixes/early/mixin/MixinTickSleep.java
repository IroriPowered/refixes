package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.PatchCounters;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import java.util.concurrent.locks.LockSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TickingThread.class)
public class MixinTickSleep {

    @Unique
    private static final long refixes$PARK_NANOS = 100_000L;

    @Unique
    private static volatile int refixes$timerState;

    // Reserve the last 1ms for parked waiting when the timer is precise.
    @Inject(method = "run", at = @At("HEAD"))
    private void refixes$reserveSpinWindow(CallbackInfo ci) {
        if (refixes$preciseTimer()) {
            TickingThread.SLEEP_OFFSET = 1_000_000L;
        }
    }

    // Replaces the pure spin-wait in the tick loop with a hybrid spin approach.
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;onSpinWait()V"))
    private void refixes$hybridWait() {
        Thread.onSpinWait();
        if (PatchCounters.ENABLED) PatchCounters.TICK_WAIT.countReached();
        if (refixes$timerState == 1) {
            if (PatchCounters.ENABLED) PatchCounters.TICK_WAIT.countEffect();
            LockSupport.parkNanos(refixes$PARK_NANOS);
        }
    }

    @Unique
    private static boolean refixes$preciseTimer() {
        int state = refixes$timerState;
        if (state == 0) {
            long worst = 0L;
            for (int i = 0; i < 5; i++) {
                long start = System.nanoTime();
                LockSupport.parkNanos(refixes$PARK_NANOS);
                worst = Math.max(worst, System.nanoTime() - start);
            }
            state = worst < 4 * refixes$PARK_NANOS ? 1 : 2;
            refixes$timerState = state;
            if (state == 2) {
                Logs.logger()
                        .atInfo()
                        .log(
                                "TickWait: a %d us park took up to %d us here, keeping vanilla's tick spin",
                                refixes$PARK_NANOS / 1_000L, worst / 1_000L);
            }
        }
        return state == 1;
    }
}
