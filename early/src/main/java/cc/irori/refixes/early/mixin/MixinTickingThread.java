package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.TickSleepOptimization;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import java.util.concurrent.locks.LockSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TickingThread.class)
public class MixinTickingThread {

    @Shadow
    private Thread thread;

    // Replaces the pure spin-wait in the tick loop with a hybrid spin approach
    @Redirect(method = "run", at = @At(value = "INVOKE", target = "Ljava/lang/Thread;onSpinWait()V"))
    private void refixes$hybridWait() {
        if (!TickSleepOptimization.enabled) {
            Thread.onSpinWait();
            return;
        }

        Thread.onSpinWait();
        // Park for 100ns, loop will re-check and spin-wait naturally as it approaches the deadline
        LockSupport.parkNanos(100_000L);
    }

    // Relaxes isInThread() to also return true for parallel entity ticking worker threads.
    @Overwrite
    public boolean isInThread() {
        Thread current = Thread.currentThread();
        if (current.equals(this.thread)) {
            return true;
        }
        if (current instanceof java.util.concurrent.ForkJoinWorkerThread fjwt) {
            return fjwt.getPool() == java.util.concurrent.ForkJoinPool.commonPool();
        }
        return false;
    }
}
