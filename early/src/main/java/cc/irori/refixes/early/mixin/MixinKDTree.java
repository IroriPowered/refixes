package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.spatial.KDTree;
import com.hypixel.hytale.component.spatial.SpatialData;
import com.hypixel.hytale.logger.HytaleLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Skips the Morton-code sort in KDTree#rebuild when entity count is small
@Mixin(KDTree.class)
public class MixinKDTree {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final boolean refixes$ENABLED =
            Boolean.parseBoolean(System.getProperty("refixes.spatialFastSort", "true"));

    @Unique
    private static final int refixes$THRESHOLD = Integer.getInteger("refixes.spatialFastSortThreshold", 64);

    static {
        refixes$LOGGER.atInfo().log(
                "KDTree fast sort: %s (threshold=%d)", refixes$ENABLED ? "ENABLED" : "DISABLED", refixes$THRESHOLD);
    }

    /**
     * Replaces sortMorton() with the simpler sort() for small datasets
     * sort() does a direct coordinate comparison sort (x > z > y)
     */
    @Redirect(
            method = "rebuild",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/component/spatial/SpatialData;sortMorton()V"))
    private void refixes$fastSort(SpatialData<?> spatialData) {
        if (refixes$ENABLED && spatialData.size() < refixes$THRESHOLD) {
            spatialData.sort();
        } else {
            spatialData.sortMorton();
        }
    }
}
