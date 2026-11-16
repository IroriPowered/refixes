package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.common.fastutil.HLongSet;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.worldmap.IWorldMap;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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

    @Unique
    private final Long2LongOpenHashMap refixes$probedAt = new Long2LongOpenHashMap();

    @Unique
    private final Long2ObjectOpenHashMap<CompletableFuture<MapImage>> refixes$probes = new Long2ObjectOpenHashMap<>();

    @Unique
    private final Long2ObjectOpenHashMap<CompletableFuture<Boolean>> refixes$terrainChecks =
            new Long2ObjectOpenHashMap<>();

    @Unique
    private boolean refixes$missing;

    @WrapOperation(
            method = "loadImages",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/worldmap/WorldMapManager;getImageAsync(J)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<MapImage> refixes$skipMissingTerrain(
            WorldMapManager manager,
            long index,
            Operation<CompletableFuture<MapImage>> original,
            @Local(argsOnly = true) World world,
            @Local(argsOnly = true, ordinal = 1) int playerChunkX,
            @Local(argsOnly = true, ordinal = 2) int playerChunkZ) {
        long now = System.nanoTime();
        if (refixes$probedAt.containsKey(index)) {
            long dx = ChunkUtil.xOfChunkIndex(index) - playerChunkX;
            long dz = ChunkUtil.zOfChunkIndex(index) - playerChunkZ;
            long near = player.getViewRadius() + 1;
            long wait = dx * dx + dz * dz <= near * near ? REFIXES$NEAR_RETRY_NANOS : REFIXES$FAR_RETRY_NANOS;
            if (now - refixes$probedAt.get(index) < wait) {
                refixes$missing = true;
                return REFIXES$MISSING;
            }
            refixes$probedAt.remove(index);
        }
        refixes$missing = false;
        CompletableFuture<MapImage> future = refixes$probes.get(index);
        if (future == null) {
            if (refixes$usesForeignBuilder(manager.getGenerator())) {
                CompletableFuture<Boolean> check = refixes$terrainChecks.get(index);
                if (check == null) {
                    check = refixes$terrainPresent(world.getChunkStore(), index);
                    if (refixes$terrainChecks.size() >= REFIXES$MAX_TRACKED) refixes$terrainChecks.clear();
                    refixes$terrainChecks.put(index, check);
                }
                if (!check.isDone()) return REFIXES$PENDING;
                refixes$terrainChecks.remove(index);
                if (check.isCompletedExceptionally() || !check.getNow(false)) return refixes$markMissing(index, now);
            }
            future = original.call(manager, index);
        }
        if (!future.isDone()) {
            if (refixes$probes.size() >= REFIXES$MAX_TRACKED) refixes$probes.clear();
            refixes$probes.put(index, future);
            return future;
        }
        refixes$probes.remove(index);
        if (future.isCompletedExceptionally() || future.getNow(null) != null) return future;
        return refixes$markMissing(index, now);
    }

    @Unique
    private CompletableFuture<MapImage> refixes$markMissing(long index, long now) {
        if (refixes$probedAt.size() >= REFIXES$MAX_TRACKED) refixes$probedAt.clear();
        refixes$probedAt.put(index, now);
        refixes$missing = true;
        return REFIXES$MISSING;
    }

    @Unique
    private static boolean refixes$usesForeignBuilder(IWorldMap generator) {
        return generator != null && !generator.getClass().getName().startsWith("com.hypixel.hytale.");
    }

    @Unique
    private static CompletableFuture<Boolean> refixes$terrainPresent(ChunkStore store, long index) {
        int x = ChunkUtil.xOfChunkIndex(index);
        int z = ChunkUtil.zOfChunkIndex(index);
        CompletableFuture<Boolean> present = CompletableFuture.completedFuture(true);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                present = present.thenCombine(
                        store.getChunkReferenceAsync(ChunkUtil.indexChunk(x + dx, z + dz), GetChunkFlags.NO_GENERATE),
                        (found, reference) -> found && reference != null && reference.isValid());
            }
        }
        return present;
    }

    @WrapOperation(
            method = "loadImages",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/common/fastutil/HLongSet;add(J)Z"),
            require = 1,
            allow = 1)
    private boolean refixes$keepMissingUnloaded(HLongSet loaded, long index, Operation<Boolean> original) {
        return !refixes$missing && original.call(loaded, index);
    }

    @Inject(method = "clear", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$forgetMissingTerrain(CallbackInfo ci) {
        refixes$probedAt.clear();
        refixes$probes.clear();
        refixes$terrainChecks.clear();
    }
}
