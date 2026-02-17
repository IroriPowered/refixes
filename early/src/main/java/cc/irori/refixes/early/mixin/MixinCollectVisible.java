package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.spatial.SpatialStructure;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectList;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * This patch uses collectCylinder() instead of collect(),
 * limiting the vertical search to 2× the view radius while using the same horizontal radius.
 * This reduces unnecessary vertical matches in worlds with entities at many Y levels
 */
@Mixin(EntityTrackerSystems.CollectVisible.class)
public abstract class MixinCollectVisible {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final boolean refixes$ENABLED =
            Boolean.parseBoolean(System.getProperty("refixes.cylinderVisibility", "true"));

    @Unique
    private static final double refixes$HEIGHT_MULTIPLIER =
            Double.parseDouble(System.getProperty("refixes.cylinderVisibilityHeightMultiplier", "2.0"));

    static {
        refixes$LOGGER.atInfo().log(
                "CollectVisible cylinder optimization: %s (heightMultiplier=%.1f)",
                refixes$ENABLED ? "ENABLED" : "DISABLED", refixes$HEIGHT_MULTIPLIER);
    }

    // Use cylindrical query instead of spherical for entity visibility collection
    @Overwrite
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        TransformComponent transformComponent =
                archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        assert transformComponent != null;
        Vector3d position = transformComponent.getPosition();

        EntityTrackerSystems.EntityViewer entityViewerComponent =
                archetypeChunk.getComponent(index, EntityTrackerSystems.EntityViewer.getComponentType());
        assert entityViewerComponent != null;

        SpatialStructure<Ref<EntityStore>> spatialStructure = store.getResource(
                        EntityModule.get().getNetworkSendableSpatialResourceType())
                .getSpatialStructure();

        ObjectList<Ref<EntityStore>> results = SpatialResource.getThreadLocalReferenceList();

        if (refixes$ENABLED) {
            double radius = entityViewerComponent.viewRadiusBlocks;
            double height = radius * refixes$HEIGHT_MULTIPLIER;
            spatialStructure.collectCylinder(position, radius, height, results);
        } else {
            spatialStructure.collect(position, entityViewerComponent.viewRadiusBlocks, results);
        }

        entityViewerComponent.visible.addAll(results);
    }
}
