package cc.irori.refixes.early.util;

import cc.irori.refixes.early.accessor.ExistingTerrainAccess;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.universe.world.World;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

public final class ExistingTerrainMap implements LongFunction<CompletableFuture<MapImage>> {

    private static final long NEAR_RETRY_NANOS = 2_000_000_000L;
    private static final long FAR_RETRY_NANOS = 30_000_000_000L;
    private static final int MAX_RECHECKS = 32;
    private static final Request MISSING = Request.missing();

    private final Long2LongLinkedOpenHashMap missingSince = new Long2LongLinkedOpenHashMap();
    private final Long2ObjectOpenHashMap<CompletableFuture<Boolean>> rechecks = new Long2ObjectOpenHashMap<>();
    private final LongArrayList due = new LongArrayList();
    private final LongArrayList dueFar = new LongArrayList();
    private World world;
    private LongFunction<CompletableFuture<MapImage>> images;
    private int centreX;
    private int centreZ;
    private IntSupplier viewRadiusSource;
    private int viewRadius;

    public ExistingTerrainMap use(
            World world,
            LongFunction<CompletableFuture<MapImage>> images,
            int centreX,
            int centreZ,
            IntSupplier viewRadius) {
        this.world = world;
        this.images = images;
        this.centreX = centreX;
        this.centreZ = centreZ;
        this.viewRadiusSource = viewRadius;
        return this;
    }

    @Override
    public CompletableFuture<MapImage> apply(long index) {
        LongFunction<CompletableFuture<MapImage>> images = this.images;
        if (world.getWorldMapManager().getWorldMapSettings().getWorldMapArea() != null) return images.apply(index);
        if (PatchCounters.ENABLED) PatchCounters.WORLD_MAP_EXISTING_TERRAIN.countReached();
        if (isWaiting(index)) {
            if (PatchCounters.ENABLED) PatchCounters.WORLD_MAP_EXISTING_TERRAIN.countEffect();
            return MISSING;
        }
        Request request = new Request();
        terrainPresent((ExistingTerrainAccess) world.getChunkStore(), index).whenComplete((present, failure) -> {
            if (failure != null || !present) {
                request.complete(null);
            } else {
                request.probed = true;
                forward(images.apply(index), request);
            }
        });
        return request;
    }

    public ExistingTerrainMap recheck() {
        if (world.getWorldMapManager().getWorldMapSettings().getWorldMapArea() != null) return this;
        long now = System.nanoTime();
        ObjectIterator<Long2ObjectMap.Entry<CompletableFuture<Boolean>>> finished =
                rechecks.long2ObjectEntrySet().fastIterator();
        while (finished.hasNext()) {
            Long2ObjectMap.Entry<CompletableFuture<Boolean>> entry = finished.next();
            long index = entry.getLongKey();
            CompletableFuture<Boolean> check = entry.getValue();
            if (!check.isDone()) continue;
            finished.remove();
            if (!missingSince.containsKey(index)) continue;
            if (!check.isCompletedExceptionally() && check.getNow(false)) {
                missingSince.remove(index);
            } else {
                missingSince.putAndMoveToLast(index, now);
            }
        }
        int free = MAX_RECHECKS - rechecks.size();
        if (free <= 0 || missingSince.isEmpty()) return this;
        viewRadius = viewRadiusSource.getAsInt();
        ExistingTerrainAccess terrain = (ExistingTerrainAccess) world.getChunkStore();
        LongIterator waiting = missingSince.keySet().iterator();
        while (waiting.hasNext() && due.size() < free) {
            long index = waiting.nextLong();
            if (rechecks.containsKey(index)) continue;
            long waited = now - missingSince.get(index);
            if (isNear(index)) {
                if (waited >= NEAR_RETRY_NANOS) due.add(index);
            } else if (waited >= FAR_RETRY_NANOS && dueFar.size() < free) {
                dueFar.add(index);
            }
        }
        for (int i = 0; i < dueFar.size() && due.size() < free; i++) due.add(dueFar.getLong(i));
        for (int i = 0; i < due.size(); i++) {
            long index = due.getLong(i);
            rechecks.put(index, terrainPresent(terrain, index));
        }
        due.clear();
        dueFar.clear();
        return this;
    }

    public boolean holdsBack(long index) {
        if (!isWaiting(index)) return false;
        if (PatchCounters.ENABLED) {
            PatchCounters.WORLD_MAP_EXISTING_TERRAIN.countReached();
            PatchCounters.WORLD_MAP_EXISTING_TERRAIN.countEffect();
        }
        return true;
    }

    public boolean isWaiting(long index) {
        return missingSince.containsKey(index);
    }

    public static boolean isMissing(CompletableFuture<MapImage> future) {
        return future instanceof Request
                && future.isDone()
                && !future.isCompletedExceptionally()
                && future.getNow(null) == null;
    }

    public static boolean isProbing(CompletableFuture<MapImage> future) {
        return future instanceof Request request && !request.probed;
    }

    public void settleMissing(long index, CompletableFuture<MapImage> future) {
        if (future == MISSING) return;
        if (PatchCounters.ENABLED) PatchCounters.WORLD_MAP_EXISTING_TERRAIN.countEffect();
        missingSince.putAndMoveToLast(index, System.nanoTime());
    }

    public void retain(LongPredicate visible) {
        missingSince.keySet().removeIf(index -> !visible.test(index));
        rechecks.keySet().removeIf(index -> !visible.test(index));
    }

    public void forget(LongSet indices) {
        LongIterator iterator = indices.iterator();
        while (iterator.hasNext()) {
            long index = iterator.nextLong();
            missingSince.remove(index);
            rechecks.remove(index);
        }
    }

    public void clear() {
        missingSince.clear();
        rechecks.clear();
    }

    private boolean isNear(long index) {
        long dx = ChunkUtil.xOfChunkIndex(index) - centreX;
        long dz = ChunkUtil.zOfChunkIndex(index) - centreZ;
        long reach = Math.max(0, 2L * viewRadius - 3);
        return 4 * (dx * dx + dz * dz) <= reach * reach;
    }

    private static CompletableFuture<Boolean> terrainPresent(ExistingTerrainAccess terrain, long index) {
        return terrain.refixes$getExistingChunk(index).thenCompose(centre -> {
            if (centre == null || !centre.isValid()) return CompletableFuture.completedFuture(false);
            int x = ChunkUtil.xOfChunkIndex(index);
            int z = ChunkUtil.zOfChunkIndex(index);
            CompletableFuture<Boolean> present = CompletableFuture.completedFuture(true);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    present = present.thenCombine(
                            terrain.refixes$getExistingChunk(ChunkUtil.indexChunk(x + dx, z + dz)),
                            (found, reference) -> found && reference != null && reference.isValid());
                }
            }
            return present;
        });
    }

    private static void forward(CompletableFuture<MapImage> source, Request target) {
        source.whenComplete((image, failure) -> {
            if (failure != null) {
                target.completeExceptionally(failure);
            } else {
                target.complete(image);
            }
        });
    }

    private static final class Request extends CompletableFuture<MapImage> {

        private volatile boolean probed;

        static Request missing() {
            Request request = new Request();
            request.complete(null);
            return request;
        }
    }
}
