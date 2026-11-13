package cc.irori.refixes.early.mixin;

import com.hypixel.fastutil.longs.Long2ObjectConcurrentHashMap;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldMapManager.class)
public abstract class MixinWorldMapManager {

    @Shadow
    @Final
    private Long2ObjectConcurrentHashMap<WorldMapManager.ImageEntry> images;

    @Shadow
    @Final
    private Long2ObjectConcurrentHashMap<CompletableFuture<MapImage>> generating;

    @Shadow
    @Final
    private Long2ObjectConcurrentHashMap<CompletableFuture<MapImage>> generationQueue;

    @Shadow
    @Final
    private static int MAX_GENERATIONS_PER_TICK;

    @Unique
    private final Object refixes$imagesLock = new Object();

    @Shadow
    private void flushGenerationQueue() {}

    @WrapMethod(method = "getImageAsync(J)Ljava/util/concurrent/CompletableFuture;", require = 1)
    private CompletableFuture<MapImage> refixes$admitRequest(
            long index,
            Operation<CompletableFuture<MapImage>> original,
            @Share("refixes$flush") LocalBooleanRef flush) {
        CompletableFuture<MapImage> result;
        synchronized (refixes$imagesLock) {
            result = original.call(index);
        }
        if (flush.get()) flushGenerationQueue();
        return result;
    }

    @WrapOperation(
            method = "getImageAsync(J)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/worldmap/WorldMapManager;flushGenerationQueue()V"),
            require = 1,
            allow = 1)
    private void refixes$deferFlush(
            WorldMapManager manager, Operation<Void> original, @Share("refixes$flush") LocalBooleanRef flush) {
        flush.set(true);
    }

    @WrapOperation(
            method = "flushGenerationQueue",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/fastutil/longs/Long2ObjectConcurrentHashMap;long2ObjectEntrySet()Lit/unimi/dsi/fastutil/objects/ObjectSet;"),
            require = 1,
            allow = 1)
    private ObjectSet<Long2ObjectMap.Entry<CompletableFuture<MapImage>>> refixes$drainRequests(
            Long2ObjectConcurrentHashMap<CompletableFuture<MapImage>> queue,
            Operation<ObjectSet<Long2ObjectMap.Entry<CompletableFuture<MapImage>>>> original,
            @Local Long2ObjectOpenHashMap<CompletableFuture<MapImage>> batch) {
        synchronized (refixes$imagesLock) {
            var iterator = original.call(queue).iterator();
            while (iterator.hasNext() && batch.size() < MAX_GENERATIONS_PER_TICK) {
                var entry = iterator.next();
                batch.put(entry.getLongKey(), entry.getValue());
                iterator.remove();
            }
        }
        return Long2ObjectMaps.<CompletableFuture<MapImage>>emptyMap().long2ObjectEntrySet();
    }

    @WrapOperation(
            method = "completeBatch",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/fastutil/longs/Long2ObjectConcurrentHashMap;remove(J)Ljava/lang/Object;"),
            require = 1,
            allow = 1)
    private Object refixes$removeCompletedRequest(
            Long2ObjectConcurrentHashMap<?> generating,
            long index,
            Operation<Object> original,
            @Local Long2ObjectMap.Entry<CompletableFuture<MapImage>> entry,
            @Local MapImage image) {
        CompletableFuture<MapImage> future = entry.getValue();
        var cached = image == null ? null : new WorldMapManager.ImageEntry(image);
        synchronized (refixes$imagesLock) {
            if (generating.remove(index, future) && cached != null) images.put(index, cached);
        }
        return null;
    }

    @WrapOperation(
            method = {"clearImages", "setGenerator"},
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/fastutil/longs/Long2ObjectConcurrentHashMap;clear()V",
                            ordinal = 0),
            require = 2,
            allow = 2)
    private void refixes$clearRequests(Long2ObjectConcurrentHashMap<?> map, Operation<Void> original) {
        List<CompletableFuture<MapImage>> cancelled;
        synchronized (refixes$imagesLock) {
            images.clear();
            generating.clear();
            cancelled = new ArrayList<>(generationQueue.values());
            generationQueue.clear();
        }
        for (var future : cancelled) future.complete(null);
    }

    @WrapOperation(
            method = {"clearImages", "setGenerator"},
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/fastutil/longs/Long2ObjectConcurrentHashMap;clear()V",
                            ordinal = 1),
            require = 2,
            allow = 2)
    private void refixes$preserveNewRequests(Long2ObjectConcurrentHashMap<?> map, Operation<Void> original) {}

    @WrapOperation(
            method = {"clearImages", "setGenerator"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/worldmap/WorldMapManager;cancelQueuedGenerations()V"),
            require = 2,
            allow = 2)
    private void refixes$preserveNewQueue(WorldMapManager manager, Operation<Void> original) {}

    @WrapOperation(
            method = "clearImagesInChunks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lit/unimi/dsi/fastutil/longs/LongSet;forEach(Ljava/util/function/LongConsumer;)V"),
            require = 1,
            allow = 1)
    private void refixes$clearChunkRequests(LongSet chunks, LongConsumer action, Operation<Void> original) {
        long[] indices = chunks.toLongArray();
        var cancelled = new ArrayList<CompletableFuture<MapImage>>();
        synchronized (refixes$imagesLock) {
            for (long index : indices) {
                images.remove(index);
                generating.remove(index);
                var future = generationQueue.remove(index);
                if (future != null) cancelled.add(future);
            }
        }
        for (var future : cancelled) future.complete(null);
    }
}
