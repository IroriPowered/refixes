package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import org.joml.Vector3d;

/**
 * Parallelizes the entity position collection phase of SpatialSystem.tick().
 * Each ForkJoin worker collects (position, ref) pairs into a local buffer,
 * then all buffers are merged sequentially into SpatialData.
 */
public final class ParallelSpatialCollector {

    private ParallelSpatialCollector() {}

    private static final class ChunkEntries<ECS_TYPE> {
        private final Vector3d[] positions;
        private final Ref<ECS_TYPE>[] refs;
        private int size;

        @SuppressWarnings("unchecked")
        private ChunkEntries(int capacity) {
            this.positions = new Vector3d[capacity];
            this.refs = (Ref<ECS_TYPE>[]) new Ref<?>[capacity];
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
        List<ForkJoinTask<ChunkEntries<ECS_TYPE>>> tasks = new ArrayList<>(chunks.size());

        Throwable[] workerFailure = new Throwable[1];
        try {
            for (ChunkWork<ECS_TYPE> work : chunks) {
                tasks.add(pool.submit(() -> {
                    try {
                        return collectChunk(work);
                    } catch (Throwable thrown) {
                        synchronized (workerFailure) {
                            workerFailure[0] = ParallelFailure.combine(workerFailure[0], thrown);
                        }
                        return null;
                    }
                }));
            }
        } catch (Error thrown) {
            throw thrown;
        } catch (Throwable thrown) {
            throw new ParallelFailure.Unrecoverable(thrown);
        }

        Throwable failure = null;
        for (ForkJoinTask<ChunkEntries<ECS_TYPE>> task : tasks) {
            try {
                task.join();
            } catch (Throwable thrown) {
                failure = ParallelFailure.combine(failure, thrown);
            }
        }
        ParallelFailure.rethrow(ParallelFailure.combine(failure, workerFailure[0]));

        // Merge results sequentially into SpatialData
        for (ForkJoinTask<ChunkEntries<ECS_TYPE>> task : tasks) {
            ChunkEntries<ECS_TYPE> entries = task.join();
            if (entries.size == 0) {
                continue;
            }
            spatialData.addCapacity(entries.size);
            for (int i = 0; i < entries.size; i++) {
                spatialData.append(entries.positions[i], entries.refs[i]);
            }
        }
    }

    private static <ECS_TYPE> ChunkEntries<ECS_TYPE> collectChunk(ChunkWork<ECS_TYPE> work) {
        int size = work.chunk.size();
        ChunkEntries<ECS_TYPE> entries = new ChunkEntries<>(size);
        for (int i = 0; i < size; i++) {
            Vector3d position = work.extractor.getPosition(work.chunk, i);
            if (position == null) {
                continue;
            }
            Ref<ECS_TYPE> ref = work.chunk.getReferenceTo(i);
            entries.positions[entries.size] = position;
            entries.refs[entries.size++] = ref;
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
