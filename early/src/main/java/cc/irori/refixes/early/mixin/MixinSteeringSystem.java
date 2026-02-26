package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.component.task.ParallelRangeTask;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enables parallel steering when PARALLEL_ENTITY_TICKING is on.
 *
 * Only activates for archetype chunks exceeding the configurable threshold
 * (default 64) to ensure the parallelism payoff exceeds the overhead and risk.
 */
@Mixin(SteeringSystem.class)
public class MixinSteeringSystem {

    @Inject(method = "isParallel", at = @At("HEAD"), cancellable = true)
    private void refixes$parallelSteering(int archetypeChunkSize, int taskCount, CallbackInfoReturnable<Boolean> cir) {
        if (EarlyOptions.isAvailable() && EarlyOptions.PARALLEL_ENTITY_TICKING.get()) {
            int threshold = EarlyOptions.PARALLEL_STEERING_THRESHOLD.get();
            if (archetypeChunkSize >= threshold) {
                cir.setReturnValue(taskCount > 0 || archetypeChunkSize > ParallelRangeTask.PARALLELISM);
            }
        }
    }
}
