package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialSystem;
import com.hypixel.hytale.logger.HytaleLogger;
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
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final boolean refixes$ENABLED =
            Boolean.parseBoolean(System.getProperty("refixes.spatialThrottle", "true"));

    @Unique
    private static final int refixes$REBUILD_INTERVAL =
            Math.max(1, Integer.getInteger("refixes.spatialRebuildInterval", 3));

    @Unique
    private int refixes$tickCounter = 0;

    static {
        refixes$LOGGER.atInfo().log(
                "SpatialSystem throttle: %s (interval=%d)",
                refixes$ENABLED ? "ENABLED" : "DISABLED", refixes$REBUILD_INTERVAL);
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void refixes$throttleSpatialRebuild(float dt, int systemIndex, Store<ECS_TYPE> store, CallbackInfo ci) {
        if (!refixes$ENABLED) {
            return;
        }

        refixes$tickCounter++;
        if (refixes$tickCounter % refixes$REBUILD_INTERVAL != 0) {
            ci.cancel();
        }
        // On rebuild ticks, let the original method run
    }
}
