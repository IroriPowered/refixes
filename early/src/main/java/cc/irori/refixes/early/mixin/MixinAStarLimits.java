package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.server.npc.navigation.AStarBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AStarBase.class)
public abstract class MixinAStarLimits {
    @Shadow
    protected int maxPathLength;

    @Shadow
    protected int openNodesLimit;

    @Shadow
    protected int totalNodesLimit;

    @Inject(method = "initComputePath", at = @At("HEAD"))
    private void refixes$applyPathfindingLimits(CallbackInfoReturnable<?> cir) {
        this.maxPathLength = EarlyOptions.PATHFINDING_MAX_PATH_LENGTH.get();
        this.openNodesLimit = EarlyOptions.PATHFINDING_OPEN_NODES_LIMIT.get();
        this.totalNodesLimit = EarlyOptions.PATHFINDING_TOTAL_NODES_LIMIT.get();
    }
}
