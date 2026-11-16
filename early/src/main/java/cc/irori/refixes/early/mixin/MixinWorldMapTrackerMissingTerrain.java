package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.ExistingTerrainAccess;
import com.hypixel.hytale.common.fastutil.HLongSet;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldMapTracker.class)
public abstract class MixinWorldMapTrackerMissingTerrain {

    @Unique
    private static final CompletableFuture<MapImage> REFIXES$MISSING = CompletableFuture.completedFuture(null);

    @Unique
    private static final CompletableFuture<MapImage> REFIXES$PENDING = new CompletableFuture<>();

    @Unique
    private static final long REFIXES$NEAR_RETRY_NANOS = 2_000_000_000L;

    @Unique
    private static final long REFIXES$FAR_RETRY_NANOS = 30_000_000_000L;

    @Unique
    private static final int REFIXES$MAX_TRACKED = 8192;

    @Shadow
    @Final
    private Player player;

    @Shadow
    private int sentViewRadius;

    @Shadow
    private int lastChunkX;

    @Shadow
    private int lastChunkZ;

    @Unique
    private final Long2LongOpenHashMap refixes$missingSince = new Long2LongOpenHashMap();

    @Unique
    private final Long2ObjectOpenHashMap<CompletableFuture<MapImage>> refixes$requests = new Long2ObjectOpenHashMap<>();

    @Unique
    private final Long2LongOpenHashMap refixes$requestSeenAt = new Long2LongOpenHashMap();

    @Unique
    private final Long2ObjectOpenHashMap<CompletableFuture<Boolean>> refixes$terrainChecks =
            new Long2ObjectOpenHashMap<>();

    @WrapOperation(
            method = "loadImages",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/worldmap/WorldMapManager;getImageAsync(J)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<MapImage> refixes$drawExistingTerrain(
            WorldMapManager manager,
            long index,
            Operation<CompletableFuture<MapImage>> original,
            @Local(argsOnly = true) World world) {
        return refixes$request(manager, index, original, world);
    }

    @WrapOperation(
            method = "processPendingReloadChunks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/worldmap/WorldMapManager;getImageAsync(J)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<MapImage> refixes$reloadExistingTerrain(
            WorldMapManager manager,
            long index,
            Operation<CompletableFuture<MapImage>> original,
            @Local(argsOnly = true) World world) {
        CompletableFuture<MapImage> future = refixes$request(manager, index, original, world);
        if (future == REFIXES$MISSING) sentViewRadius = 0;
        return future;
    }

    @WrapOperation(
            method = "processPendingReloadChunks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;put(JLjava/lang/Object;)Ljava/lang/Object;"),
            require = 1,
            allow = 1)
    private Object refixes$askAgainWhilePending(
            Long2ObjectOpenHashMap<CompletableFuture<MapImage>> futures,
            long index,
            Object future,
            Operation<Object> original) {
        return future == REFIXES$PENDING ? null : original.call(futures, index, future);
    }

    @WrapOperation(
            method = {"loadImages", "processPendingReloadChunks"},
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/common/fastutil/HLongSet;add(J)Z"),
            require = 2,
            allow = 2)
    private boolean refixes$keepMissingUnloaded(
            HLongSet loaded, long index, Operation<Boolean> original, @Local CompletableFuture<MapImage> future) {
        return future != REFIXES$MISSING && original.call(loaded, index);
    }

    @Inject(
            method = "clear",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/common/fastutil/HLongSet;clear()V"),
            require = 1,
            allow = 1)
    private void refixes$forgetMissingTerrain(CallbackInfo ci) {
        refixes$missingSince.clear();
        refixes$requests.clear();
        refixes$requestSeenAt.clear();
        refixes$terrainChecks.clear();
    }

    @Inject(
            method = "clearChunks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lit/unimi/dsi/fastutil/longs/LongSet;forEach(Ljava/util/function/LongConsumer;)V"),
            require = 1,
            allow = 1)
    private void refixes$forgetClearedChunks(LongSet chunks, CallbackInfo ci) {
        LongIterator iterator = chunks.iterator();
        while (iterator.hasNext()) {
            long index = iterator.nextLong();
            refixes$missingSince.remove(index);
            refixes$requests.remove(index);
            refixes$requestSeenAt.remove(index);
            refixes$terrainChecks.remove(index);
        }
    }

    @Unique
    private CompletableFuture<MapImage> refixes$request(
            WorldMapManager manager, long index, Operation<CompletableFuture<MapImage>> original, World world) {
        if (manager.getWorldMapSettings().getWorldMapArea() != null) return original.call(manager, index);
        long now = System.nanoTime();
        if (refixes$isWaiting(index, now)) return REFIXES$MISSING;
        CompletableFuture<MapImage> request = refixes$requests.get(index);
        if (request != null) {
            if (!request.isDone()) {
                refixes$requestSeenAt.put(index, now);
                return REFIXES$PENDING;
            }
            refixes$requests.remove(index);
            boolean watched = now - refixes$requestSeenAt.remove(index) < REFIXES$FAR_RETRY_NANOS;
            if (refixes$isEmpty(request)) return refixes$markMissing(index, now);
            if (watched) return request;
        } else {
            CompletableFuture<MapImage> blocked = refixes$awaitTerrain(world, index, now);
            if (blocked != null) return blocked;
        }
        CompletableFuture<MapImage> future = original.call(manager, index);
        if (!future.isDone()) {
            if (refixes$requests.size() >= REFIXES$MAX_TRACKED) {
                refixes$requests.clear();
                refixes$requestSeenAt.clear();
            }
            refixes$requests.put(index, future);
            refixes$requestSeenAt.put(index, now);
            return REFIXES$PENDING;
        }
        return refixes$isEmpty(future) ? refixes$markMissing(index, now) : future;
    }

    @Unique
    private boolean refixes$isWaiting(long index, long now) {
        if (!refixes$missingSince.containsKey(index)) return false;
        long dx = ChunkUtil.xOfChunkIndex(index) - lastChunkX;
        long dz = ChunkUtil.zOfChunkIndex(index) - lastChunkZ;
        long reach = Math.max(0, 2L * player.getViewRadius() - 3);
        long wait = 4 * (dx * dx + dz * dz) <= reach * reach ? REFIXES$NEAR_RETRY_NANOS : REFIXES$FAR_RETRY_NANOS;
        if (now - refixes$missingSince.get(index) < wait) return true;
        refixes$missingSince.remove(index);
        return false;
    }

    @Unique
    private CompletableFuture<MapImage> refixes$awaitTerrain(World world, long index, long now) {
        CompletableFuture<Boolean> check = refixes$terrainChecks.remove(index);
        if (check == null) check = refixes$terrainPresent((ExistingTerrainAccess) world.getChunkStore(), index);
        if (!check.isDone()) {
            if (refixes$terrainChecks.size() >= REFIXES$MAX_TRACKED) refixes$terrainChecks.clear();
            refixes$terrainChecks.put(index, check);
            return REFIXES$PENDING;
        }
        return check.isCompletedExceptionally() || !check.getNow(false) ? refixes$markMissing(index, now) : null;
    }

    @Unique
    private CompletableFuture<MapImage> refixes$markMissing(long index, long now) {
        if (refixes$missingSince.size() >= REFIXES$MAX_TRACKED) refixes$missingSince.clear();
        refixes$missingSince.put(index, now);
        return REFIXES$MISSING;
    }

    @Unique
    private static boolean refixes$isEmpty(CompletableFuture<MapImage> future) {
        return !future.isCompletedExceptionally() && future.getNow(null) == null;
    }

    @Unique
    private static CompletableFuture<Boolean> refixes$terrainPresent(ExistingTerrainAccess terrain, long index) {
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
}
