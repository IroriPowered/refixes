package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.ParallelFailure;
import com.hypixel.hytale.component.task.ParallelRangeTask;
import com.hypixel.hytale.component.task.ParallelTask;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CountedCompleter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParallelTask.class)
public abstract class MixinParallelTask implements ParallelFailure.Managed {
    @Shadow
    private ParallelRangeTask<?>[] subTasks;

    @Shadow
    private int size;

    @Shadow
    private volatile boolean running;

    @Override
    public boolean refixes$isManagedInvocation() {
        return running;
    }

    @WrapMethod(method = "doInvoke()V", require = 1)
    private void refixes$drainBeforeReuse(Operation<Void> original) {
        if (running) throw new IllegalStateException("Parallel task has already been started");
        try {
            original.call();
        } catch (Exception thrown) {
            throw new ParallelFailure.Unrecoverable(thrown);
        }
        Throwable failure = null;
        for (int i = 0; i < size; i++) {
            failure = ParallelFailure.combine(failure, ((ParallelFailure.Access) subTasks[i]).refixes$takeFailure());
        }
        if (failure != null) {
            for (int x = 0; x < size; x++) {
                ParallelRangeTask<?> range = subTasks[x];
                for (int i = 0; i < range.size(); i++) {
                    if (range.get(i) instanceof ParallelFailure.Discardable data) {
                        try {
                            data.clear();
                        } catch (Throwable thrown) {
                            failure = ParallelFailure.combine(failure, thrown);
                        }
                    }
                }
            }
        }
        ParallelFailure.rethrow(failure);
    }

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void refixes$completeEmptyBatch(CallbackInfo ci) {
        if (size == 0) {
            ((CountedCompleter<?>) (Object) this).propagateCompletion();
            ci.cancel();
        }
    }
}
