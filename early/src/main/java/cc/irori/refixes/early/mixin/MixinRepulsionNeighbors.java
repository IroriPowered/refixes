package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.PatchCounters;
import com.hypixel.hytale.component.spatial.SpatialStructure;
import com.hypixel.hytale.server.core.modules.entity.repulsion.RepulsionSystems;
import java.util.List;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RepulsionSystems.RepulsionTicker.class)
public class MixinRepulsionNeighbors {

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/spatial/SpatialStructure;ordered(Lorg/joml/Vector3dc;DLjava/util/List;)V"),
            require = 1,
            allow = 1)
    private void refixes$collectNeighbors(
            SpatialStructure<Object> structure, Vector3dc position, double radius, List<Object> results) {
        if (PatchCounters.ENABLED) {
            PatchCounters.REPULSION_NEIGHBORS.countReached();
            PatchCounters.REPULSION_NEIGHBORS.countEffect();
        }
        structure.collect(position, radius, results);
    }
}
