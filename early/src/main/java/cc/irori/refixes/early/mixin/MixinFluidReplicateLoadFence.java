package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.ChunkTrackerSectionFence;
import com.hypixel.hytale.builtin.fluid.FluidSystems;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FluidSystems.ReplicateChanges.class)
public abstract class MixinFluidReplicateLoadFence {

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/modules/entity/player/ChunkTracker;isLoaded(III)Z"),
            require = 2,
            allow = 2)
    private boolean refixes$markSkippedWhileLoading(
            ChunkTracker tracker, int x, int y, int z, Operation<Boolean> original) {
        if (original.call(tracker, x, y, z)) return true;
        if (((ChunkTrackerSectionFence) tracker).refixes$markDirtyIfStillLoading(x, y, z)) return false;
        return original.call(tracker, x, y, z);
    }
}
