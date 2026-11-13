package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.server.npc.navigation.AStarBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AStarBase.class)
public abstract class MixinAStarLimits {
    @Shadow
    protected int maxPathLength;

    @Shadow
    protected int openNodesLimit;

    @Shadow
    protected int totalNodesLimit;

    @Unique
    private Integer refixes$roleMaxPathLength;

    @Unique
    private Integer refixes$roleOpenNodesLimit;

    @Unique
    private Integer refixes$roleTotalNodesLimit;

    @Inject(method = "setMaxPathLength", at = @At("TAIL"), require = 1)
    private void refixes$rememberMaxPathLength(int value, CallbackInfo ci) {
        refixes$roleMaxPathLength = value;
    }

    @Inject(method = "setOpenNodesLimit", at = @At("TAIL"), require = 1)
    private void refixes$rememberOpenNodesLimit(int value, CallbackInfo ci) {
        refixes$roleOpenNodesLimit = value;
    }

    @Inject(method = "setTotalNodesLimit", at = @At("TAIL"), require = 1)
    private void refixes$rememberTotalNodesLimit(int value, CallbackInfo ci) {
        refixes$roleTotalNodesLimit = value;
    }

    @Inject(method = "initComputePath", at = @At("HEAD"))
    private void refixes$applyPathfindingLimits(CallbackInfoReturnable<?> cir) {
        if (refixes$roleMaxPathLength == null) refixes$roleMaxPathLength = maxPathLength;
        if (refixes$roleOpenNodesLimit == null) refixes$roleOpenNodesLimit = openNodesLimit;
        if (refixes$roleTotalNodesLimit == null) refixes$roleTotalNodesLimit = totalNodesLimit;
        this.maxPathLength = refixes$tighter(refixes$roleMaxPathLength, EarlyOptions.PATHFINDING_MAX_PATH_LENGTH.get());
        this.openNodesLimit =
                refixes$tighter(refixes$roleOpenNodesLimit, EarlyOptions.PATHFINDING_OPEN_NODES_LIMIT.get());
        this.totalNodesLimit =
                refixes$tighter(refixes$roleTotalNodesLimit, EarlyOptions.PATHFINDING_TOTAL_NODES_LIMIT.get());
    }

    @Unique
    private static int refixes$tighter(int role, int cap) {
        if (role <= 0) return cap;
        if (cap <= 0) return role;
        return Math.min(role, cap);
    }
}
