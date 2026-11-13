package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.PathfindingBudget;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorldPathfindingBudget {
    @Shadow
    @Final
    private EntityStore entityStore;

    @Inject(method = "tick(F)V", at = @At("HEAD"))
    private void refixes$resetPathfindingBudget(float dt, CallbackInfo ci) {
        if (this.entityStore == null) return;
        Store<?> store = this.entityStore.getStore();
        if (store != null) {
            PathfindingBudget.reset(
                    store,
                    EarlyOptions.PATHFINDING_MAX_NEW_SEARCHES_PER_TICK.get(),
                    EarlyOptions.PATHFINDING_MAX_NODE_EXPANSIONS_PER_TICK.get());
        }
    }

    @Inject(method = "onShutdown", at = @At("HEAD"))
    private void refixes$dropPathfindingBudget(CallbackInfo ci) {
        if (this.entityStore != null) PathfindingBudget.remove(this.entityStore.getStore());
    }
}
