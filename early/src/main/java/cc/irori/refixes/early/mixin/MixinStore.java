package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Store;
import java.util.concurrent.ForkJoinWorkerThread;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Relaxes the thread assertion to allow parallel entity ticking.
 * This patch allows ForkJoinWorkerThread through the assertion,
 * while still catching genuine off-thread access from other contexts.
 */
@Mixin(Store.class)
public abstract class MixinStore<ECS_TYPE> {

    @Shadow
    @Final
    private Thread thread;

    // Allow ForkJoinPool worker threads used by parallel entity ticking
    @Overwrite
    public void assertThread() {
        Thread currentThread = Thread.currentThread();
        if (currentThread instanceof ForkJoinWorkerThread) {
            return;
        }
        if (!currentThread.equals(this.thread) && this.thread.isAlive()) {
            throw new IllegalStateException(
                    "Assert not in thread! " + this.thread + " but was in " + currentThread);
        }
    }
}
