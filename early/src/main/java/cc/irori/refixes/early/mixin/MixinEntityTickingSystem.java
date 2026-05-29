package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.task.ParallelRangeTask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enables parallel entity ticking for systems that opt in via isParallel()
 * This patch makes maybeUseParallel return true when the chunk is large enough to benefit from parallelism
 */
@Mixin(EntityTickingSystem.class)
public class MixinEntityTickingSystem {

    // Enable parallel entity ticking when chunk size is large enough
    @Inject(method = "maybeUseParallel", at = @At("HEAD"), cancellable = true)
    private static void maybeUseParallel(int archetypeChunkSize, int taskCount, CallbackInfoReturnable<Boolean> cir) {
        cir.cancel();
        cir.setReturnValue(taskCount > 0 || archetypeChunkSize > ParallelRangeTask.PARALLELISM);
    }
}
