package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.PatchCounters;
import com.hypixel.hytale.component.spatial.KDTree;
import com.hypixel.hytale.component.spatial.SpatialData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Skips the Morton-code sort in KDTree#rebuild when entity count is small
@Mixin(KDTree.class)
public class MixinKDTree {

    /**
     * Replaces sortMorton() with the simpler sort() for small datasets
     * sort() does a direct coordinate comparison sort (x > z > y)
     */
    @Redirect(
            method = "rebuild",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/component/spatial/SpatialData;sortMorton()V"))
    private void refixes$fastSort(SpatialData<?> spatialData) {
        if (PatchCounters.ENABLED) PatchCounters.KD_TREE.countReached();
        if (spatialData.size() < EarlyOptions.KDTREE_OPTIMIZATION_THRESHOLD.get()) {
            if (PatchCounters.ENABLED) PatchCounters.KD_TREE.countEffect();
            spatialData.sort();
        } else {
            spatialData.sortMorton();
        }
    }
}
