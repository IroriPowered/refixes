package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialData;
import com.hypixel.hytale.math.vector.Vector3d;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

/**
 * Parallelizes the entity position collection phase of SpatialSystem.tick().
 * Each ForkJoin worker collects (position, ref) pairs into a local buffer,
 * then all buffers are merged sequentially into SpatialData.
 */
public final class ParallelSpatialCollector {

    private ParallelSpatialCollector() {}

    // A collected spatial entry: position + entity reference.
    public static final class Entry<ECS_TYPE> {
        public final double x, y, z;
        public final Ref<ECS_TYPE> ref;

        public Entry(double x, double y, double z, Ref<ECS_TYPE> ref) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.ref = ref;
        }
    }

    // A work unit: one archetype chunk to collect positions from.
    public static final class ChunkWork<ECS_TYPE> {
        public final ArchetypeChunk<ECS_TYPE> chunk;
        public final PositionExtractor<ECS_TYPE> extractor;

        public ChunkWork(ArchetypeChunk<ECS_TYPE> chunk, PositionExtractor<ECS_TYPE> extractor) {
            this.chunk = chunk;
            this.extractor = extractor;
        }
    }

    @FunctionalInterface
    public interface PositionExtractor<ECS_TYPE> {
        Vector3d getPosition(ArchetypeChunk<ECS_TYPE> chunk, int index);
    }

    // Collects entity positions from the given chunks in parallel, then merges into spatialData.
    public static <ECS_TYPE> void collectParallel(
            List<ChunkWork<ECS_TYPE>> chunks, SpatialData<Ref<ECS_TYPE>> spatialData) {
        if (chunks.isEmpty()) {
            return;
        }

        // For small workloads, collect sequentially
        if (chunks.size() <= 2) {
            collectSequential(chunks, spatialData);
            return;
        }

        ForkJoinPool pool = ForkJoinPool.commonPool();
        List<ForkJoinTask<List<Entry<ECS_TYPE>>>> tasks = new ArrayList<>(chunks.size());

        for (ChunkWork<ECS_TYPE> work : chunks) {
            tasks.add(pool.submit(() -> collectChunk(work)));
        }

        // Merge results sequentially into SpatialData
        for (ForkJoinTask<List<Entry<ECS_TYPE>>> task : tasks) {
            List<Entry<ECS_TYPE>> entries = task.join();
            if (entries.isEmpty()) {
                continue;
            }
            spatialData.addCapacity(entries.size());
            for (Entry<ECS_TYPE> entry : entries) {
                spatialData.append(new Vector3d(entry.x, entry.y, entry.z), entry.ref);
            }
        }
    }

    private static <ECS_TYPE> List<Entry<ECS_TYPE>> collectChunk(ChunkWork<ECS_TYPE> work) {
        int size = work.chunk.size();
        List<Entry<ECS_TYPE>> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Vector3d position = work.extractor.getPosition(work.chunk, i);
            if (position == null) {
                continue;
            }
            Ref<ECS_TYPE> ref = work.chunk.getReferenceTo(i);
            entries.add(new Entry<>(position.x, position.y, position.z, ref));
        }
        return entries;
    }

    private static <ECS_TYPE> void collectSequential(
            List<ChunkWork<ECS_TYPE>> chunks, SpatialData<Ref<ECS_TYPE>> spatialData) {
        for (ChunkWork<ECS_TYPE> work : chunks) {
            int size = work.chunk.size();
            spatialData.addCapacity(size);
            for (int i = 0; i < size; i++) {
                Vector3d position = work.extractor.getPosition(work.chunk, i);
                if (position == null) {
                    continue;
                }
                Ref<ECS_TYPE> ref = work.chunk.getReferenceTo(i);
                spatialData.append(position, ref);
            }
        }
    }
}
