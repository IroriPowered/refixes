package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.accessor.TickingThreadRunAccess;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(TickingThread.class)
public abstract class MixinTickingThreadAbandon {

    private static final String ABANDON = "abandon(Lcom/hypixel/hytale/server/core/util/thread/TickingThread$Run;)V";

    @WrapOperation(
            method = ABANDON,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/atomic/AtomicReference;compareAndSet(Ljava/lang/Object;Ljava/lang/Object;)Z",
                            ordinal = 0),
            require = 1,
            allow = 1)
    private boolean refixes$claimShutdownHook(
            AtomicReference<Object> currentRun, Object run, Object takeover, Operation<Boolean> original) {
        TickingThreadRunAccess access = (TickingThreadRunAccess) run;
        if (!access.refixes$getNeedsShutdown().getAndSet(false)) {
            Logs.logger()
                    .atSevere()
                    .log(
                            "TickingThread %s is still in its shutdown hook, leaving it to finish",
                            access.refixes$getThread());
            return false;
        }
        if (!original.call(currentRun, run, takeover)) original.call(currentRun, null, takeover);
        return true;
    }

    @WrapOperation(
            method = ABANDON,
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicBoolean;getAndSet(Z)Z"),
            require = 1,
            allow = 1)
    private boolean refixes$runClaimedShutdownHook(
            AtomicBoolean needsShutdown, boolean value, Operation<Boolean> original) {
        original.call(needsShutdown, value);
        return true;
    }
}
