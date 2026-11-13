package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import java.util.Deque;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorldTaskQueueClose extends TickingThread {

    @Shadow
    @Final
    private AtomicBoolean acceptingTasks;

    @Shadow
    @Final
    private Deque<Runnable> taskQueue;

    @Shadow
    @Final
    private String name;

    protected MixinWorldTaskQueueClose(String threadName) {
        super(threadName);
    }

    @Inject(method = "execute(Ljava/lang/Runnable;)V", at = @At("RETURN"), require = 1, allow = 1)
    private void refixes$rejectAfterClose(Runnable command, CallbackInfo ci) {
        if (!acceptingTasks.get() && taskQueue.remove(command)) {
            throw new SkipSentryException(new IllegalThreadStateException(
                    "World thread is not accepting tasks: " + name + ", " + getThread()));
        }
    }

    @Inject(
            method = "onShutdown()V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/util/concurrent/atomic/AtomicBoolean;set(Z)V",
                            shift = At.Shift.AFTER),
            require = 1,
            allow = 1)
    private void refixes$cancelQueuedTasks(CallbackInfo ci) {
        Runnable queued;
        while ((queued = taskQueue.poll()) != null) {
            if (queued instanceof Future<?> future) future.cancel(false);
        }
    }
}
