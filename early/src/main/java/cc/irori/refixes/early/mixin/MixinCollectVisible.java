package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.spatial.SpatialStructure;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * This patch uses collectCylinder() with the same horizontal radius.
 * At the default height multiplier of 2.0, the cylinder contains the native sphere
 * and includes additional vertical matches.
 */
@Mixin(EntityTrackerSystems.CollectVisible.class)
public abstract class MixinCollectVisible {

    /**
     * @author Refixes
     * @reason Use cylindrical query instead of spherical for entity visibility collection
     */
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

        List<Ref<EntityStore>> results = SpatialResource.getThreadLocalReferenceList();

        double radius = entityViewerComponent.viewRadiusBlocks;
        double height = radius * EarlyOptions.CYLINDER_VISIBILITY_HEIGHT_MULTIPLIER.get();
        spatialStructure.collectCylinder(position, radius, height, results);

        entityViewerComponent.visible.addAll(results);
    }
}
