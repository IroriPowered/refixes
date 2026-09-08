package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.ParallelFailure;
import com.hypixel.hytale.component.task.ParallelRangeTask;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CountedCompleter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParallelRangeTask.class)
public abstract class MixinParallelRangeTask implements ParallelFailure.Access {
    @Shadow
    private int size;

    @Unique
    private Throwable refixes$failure;

    @Override
    public synchronized void refixes$recordFailure(Throwable failure) {
        refixes$failure = ParallelFailure.combine(refixes$failure, failure);
    }

    @Override
    public synchronized Throwable refixes$takeFailure() {
        Throwable failure = refixes$failure;
        refixes$failure = null;
        return failure;
    }

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void refixes$completeEmptyRange(CallbackInfo ci) {
        if (size == 0) {
            ((CountedCompleter<?>) (Object) this).propagateCompletion();
            ci.cancel();
        }
    }

    @Mixin(targets = "com.hypixel.hytale.component.task.ParallelRangeTask$SubTask")
    public abstract static class SubTask {
        @WrapMethod(method = "compute")
        private void refixes$completeFailedWorker(Operation<Void> original) {
            CountedCompleter<?> task = (CountedCompleter<?>) (Object) this;
            CountedCompleter<?> range = task.getCompleter();
            if (!(range.getCompleter() instanceof ParallelFailure.Managed parent)
                    || !parent.refixes$isManagedInvocation()) {
                original.call();
                return;
            }
            try {
                original.call();
                return;
            } catch (Throwable failure) {
                ((ParallelFailure.Access) range).refixes$recordFailure(failure);
            }
            task.propagateCompletion();
        }
    }
}
