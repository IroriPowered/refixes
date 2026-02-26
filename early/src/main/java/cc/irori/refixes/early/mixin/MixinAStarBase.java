package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.server.npc.navigation.AStarBase;
import com.hypixel.hytale.server.npc.navigation.AStarNode;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimizes A* pathfinding:
 * Binary search insert for open nodes (replaces O(n) linear scan)
 * Configurable limits for maxPathLength, openNodesLimit, totalNodesLimit
 */
@Mixin(AStarBase.class)
public class MixinAStarBase {

    @Shadow
    protected List<AStarNode> openNodes;

    @Shadow
    protected Long2ObjectMap<AStarNode> visitedBlocks;

    @Shadow
    protected int maxPathLength;

    @Shadow
    protected int openNodesLimit;

    @Shadow
    protected int totalNodesLimit;

    @Inject(
            method = "addOpenNode(Lcom/hypixel/hytale/server/npc/navigation/AStarNode;J)V",
            at = @At("HEAD"),
            cancellable = true)
    private void refixes$binarySearchInsert(AStarNode node, long index, CallbackInfo ci) {
        float totalCost = node.getTotalCost();

        int lo = 0, hi = openNodes.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (openNodes.get(mid).getTotalCost() < totalCost) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        openNodes.add(lo, node);
        visitedBlocks.put(index, node);

        ci.cancel();
    }

    @Inject(method = "initComputePath", at = @At("HEAD"))
    private void refixes$applyPathfindingLimits(CallbackInfoReturnable<?> cir) {
        if (!EarlyOptions.isAvailable()) {
            return;
        }
        this.maxPathLength = EarlyOptions.PATHFINDING_MAX_PATH_LENGTH.get();
        this.openNodesLimit = EarlyOptions.PATHFINDING_OPEN_NODES_LIMIT.get();
        this.totalNodesLimit = EarlyOptions.PATHFINDING_TOTAL_NODES_LIMIT.get();
    }
}
