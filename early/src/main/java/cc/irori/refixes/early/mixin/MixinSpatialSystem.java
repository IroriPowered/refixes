package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Throttles KDTree rebuilds in SpatialSystem to reduce per-tick cost
 * This patch skips the rebuild on intermediate ticks
 */
@Mixin(SpatialSystem.class)
public abstract class MixinSpatialSystem<ECS_TYPE> {

    @Unique
    private int refixes$tickCounter = 0;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void refixes$throttleSpatialRebuild(float dt, int systemIndex, Store<ECS_TYPE> store, CallbackInfo ci) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.KDTREE_OPTIMIZATION_THROTTLE.get()) {
            return;
        }

        refixes$tickCounter++;
        if (refixes$tickCounter % EarlyOptions.KDTREE_OPTIMIZATION_REBUILD_INTERVAL.get() != 0) {
            ci.cancel();
        }
        // On rebuild ticks, let the original method run
    }
}
