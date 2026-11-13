package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.TickingThreadRunAccess;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.concurrent.atomic.AtomicReference;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TickingThread.class)
public abstract class MixinTickingThreadStart {
    @Shadow
    @Final
    private AtomicReference<?> currentRun;

    @WrapMethod(method = "run()V", require = 1)
    private void refixes$settleFailedStartup(
            Operation<Void> original, @Share("startupFailure") LocalRef<Throwable> startupFailure) {
        Object run = currentRun.get();
        if (!(run instanceof TickingThreadRunAccess owner) || owner.refixes$getThread() != Thread.currentThread()) {
            original.call();
            return;
        }
        try {
            original.call();
        } finally {
            if (!owner.refixes$getStartedFuture().isDone()) {
                Throwable failure = startupFailure.get();
                if (failure == null)
                    failure = new IllegalStateException("Ticking thread stopped before startup completed");
                owner.refixes$getStartedFuture().completeExceptionally(failure);
            }
        }
    }

    @WrapOperation(
            method = "run()V",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/util/thread/TickingThread;onStart()V"),
            require = 1,
            allow = 1)
    private void refixes$captureStartupFailure(
            TickingThread thread, Operation<Void> original, @Share("startupFailure") LocalRef<Throwable> startupFailure)
            throws Throwable {
        try {
            original.call(thread);
        } catch (Throwable failure) {
            startupFailure.set(failure);
            throw failure;
        }
    }
}
