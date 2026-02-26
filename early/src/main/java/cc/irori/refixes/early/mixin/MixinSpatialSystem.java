package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.ParallelSpatialCollector;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialData;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.spatial.SpatialSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Parallelizes the entity position collection phase of SpatialSystem.tick().
 * The sequential forEachChunk loop is replaced with parallel per-chunk collection
 * into thread-local buffers, followed by a sequential merge into SpatialData.
 * The KDTree rebuild() remains single-threaded.
 */
@Mixin(SpatialSystem.class)
public abstract class MixinSpatialSystem<ECS_TYPE> {

    @Shadow
    @Final
    private ResourceType<ECS_TYPE, SpatialResource<Ref<ECS_TYPE>, ECS_TYPE>> resourceType;

    @Shadow
    public abstract Vector3d getPosition(@Nonnull ArchetypeChunk<ECS_TYPE> chunk, int index);

    @Inject(method = "tick(FILcom/hypixel/hytale/component/Store;)V", at = @At("HEAD"), cancellable = true)
    private void refixes$parallelCollect(float dt, int systemIndex, Store<ECS_TYPE> store, CallbackInfo ci) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.PARALLEL_SPATIAL_COLLECTION.get()) {
            return;
        }

        ci.cancel();

        SpatialResource<Ref<ECS_TYPE>, ECS_TYPE> spatialResource = store.getResource(this.resourceType);
        SpatialData<Ref<ECS_TYPE>> spatialData = spatialResource.getSpatialData();
        spatialData.clear();

        // Collect matching archetype chunks via forEachChunk API
        List<ParallelSpatialCollector.ChunkWork<ECS_TYPE>> chunks = new ArrayList<>();
        store.forEachChunk(systemIndex, (archetypeChunk, commandBuffer) -> {
            chunks.add(new ParallelSpatialCollector.ChunkWork<>(archetypeChunk, this::getPosition));
        });

        // Parallel collection and sequential merge into SpatialData
        ParallelSpatialCollector.collectParallel(chunks, spatialData);

        // Rebuild the spatial structure (KDTree) single-threaded
        spatialResource.getSpatialStructure().rebuild(spatialData);
    }
}
