package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.spatial.SpatialData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import org.joml.Vector3d;

/**
 * Parallelizes the entity position collection phase of SpatialSystem.tick().
 * Each ForkJoin worker collects (position, ref) pairs into a local buffer,
 * then all buffers are merged sequentially into SpatialData.
 */
public final class ParallelSpatialCollector {
    private static final int MIN_PARALLEL_CANDIDATES = 4096;
    private static final int MAX_BATCHES = 32;
    private static final int ENTRIES_PER_SEGMENT = 256;

    private ParallelSpatialCollector() {}

    private static final class Segment<ECS_TYPE> {
        private final Vector3d[] positions;
        private final Ref<ECS_TYPE>[] refs;
        private Segment<ECS_TYPE> next;
        private int size;

        @SuppressWarnings("unchecked")
        private Segment(int capacity) {
            this.positions = new Vector3d[capacity];
            this.refs = (Ref<ECS_TYPE>[]) new Ref<?>[capacity];
        }
    }

    private static final class Entries<ECS_TYPE> {
        private final int segmentCapacity;
        private Segment<ECS_TYPE> head;
        private Segment<ECS_TYPE> tail;
        private int size;

        private Entries(long candidates) {
            this.segmentCapacity = (int) Math.min(ENTRIES_PER_SEGMENT, Math.max(1, candidates));
        }

        private void append(Vector3d position, Ref<ECS_TYPE> ref) {
            if (tail == null || tail.size == tail.positions.length) {
                Segment<ECS_TYPE> next = new Segment<>(segmentCapacity);
                if (tail == null) head = next;
                else tail.next = next;
                tail = next;
            }
            tail.positions[tail.size] = position;
            tail.refs[tail.size++] = ref;
            size++;
        }
    }

    private static final class Batch<ECS_TYPE> extends RecursiveTask<Entries<ECS_TYPE>> {
        private final List<ChunkWork<ECS_TYPE>> chunks;
        private final int start;
        private final int end;
        private final long candidates;
        private boolean started;
        private boolean discarded;
        private Throwable failure;

        private Batch(List<ChunkWork<ECS_TYPE>> chunks, int start, int end, long candidates) {
            this.chunks = chunks;
            this.start = start;
            this.end = end;
            this.candidates = candidates;
        }

        @Override
        protected Entries<ECS_TYPE> compute() {
            synchronized (this) {
                if (discarded) return null;
                started = true;
            }
            try {
                return collectBatch(chunks, start, end, candidates);
            } catch (Throwable thrown) {
                failure = thrown;
                return null;
            }
        }

        private synchronized void discardIfUnstarted() {
            if (!started) {
                discarded = true;
                complete(null);
            }
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
        // For small workloads, collect sequentially
        if (chunks.size() <= 2) {
            collectSequential(chunks, spatialData);
            return;
        }
        collectParallel(chunks, spatialData, ForkJoinPool.commonPool());
    }

    static <ECS_TYPE> void collectParallel(
            List<ChunkWork<ECS_TYPE>> chunks, SpatialData<Ref<ECS_TYPE>> spatialData, ForkJoinPool pool) {
        if (chunks.isEmpty()) return;
        int parallelism = pool.getParallelism();
        if (chunks.size() <= 2 || parallelism <= 1) {
            collectSequential(chunks, spatialData);
            return;
        }

        long candidates = 0;
        for (ChunkWork<ECS_TYPE> work : chunks) candidates += work.chunk.size();
        if (candidates < MIN_PARALLEL_CANDIDATES) {
            collectSequential(chunks, spatialData);
            return;
        }

        int batchLimit = (int) Math.min(chunks.size(), Math.min(MAX_BATCHES, (long) parallelism * 2));
        long target = (candidates + batchLimit - 1) / batchLimit;
        List<Batch<ECS_TYPE>> batches = new ArrayList<>(batchLimit);
        int start = 0;
        long batchCandidates = 0;
        for (int i = 0; i < chunks.size(); i++) {
            batchCandidates += chunks.get(i).chunk.size();
            if (batchCandidates >= target && batches.size() < batchLimit - 1) {
                batches.add(new Batch<>(chunks, start, i + 1, batchCandidates));
                start = i + 1;
                batchCandidates = 0;
            }
        }
        if (start < chunks.size()) batches.add(new Batch<>(chunks, start, chunks.size(), batchCandidates));

        Throwable failure = null;
        int submitted = 0;
        for (Batch<ECS_TYPE> batch : batches) {
            submitted++;
            try {
                pool.execute(batch);
            } catch (Throwable thrown) {
                failure = thrown;
                for (int i = 0; i < submitted; i++) batches.get(i).discardIfUnstarted();
                break;
            }
        }
        for (int i = 0; i < submitted; i++) {
            Batch<ECS_TYPE> batch = batches.get(i);
            try {
                batch.join();
            } catch (Throwable thrown) {
                failure = ParallelFailure.combine(failure, thrown);
            }
            failure = ParallelFailure.combine(failure, batch.failure);
        }
        ParallelFailure.rethrow(failure);

        // Merge results sequentially into SpatialData
        for (Batch<ECS_TYPE> batch : batches) {
            Entries<ECS_TYPE> entries = batch.getRawResult();
            if (entries == null) continue;
            spatialData.addCapacity(entries.size);
            for (Segment<ECS_TYPE> segment = entries.head; segment != null; segment = segment.next) {
                for (int i = 0; i < segment.size; i++) {
                    spatialData.append(segment.positions[i], segment.refs[i]);
                }
            }
        }
    }

    private static <ECS_TYPE> Entries<ECS_TYPE> collectBatch(
            List<ChunkWork<ECS_TYPE>> chunks, int start, int end, long candidates) {
        Entries<ECS_TYPE> entries = null;
        for (int c = start; c < end; c++) {
            ChunkWork<ECS_TYPE> work = chunks.get(c);
            int size = work.chunk.size();
            for (int i = 0; i < size; i++) {
                Vector3d position = work.extractor.getPosition(work.chunk, i);
                if (position == null) continue;
                Ref<ECS_TYPE> ref = work.chunk.getReferenceTo(i);
                if (entries == null) entries = new Entries<>(candidates);
                entries.append(position, ref);
            }
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
                if (position == null) continue;
                Ref<ECS_TYPE> ref = work.chunk.getReferenceTo(i);
                spatialData.append(position, ref);
            }
        }
    }
}
