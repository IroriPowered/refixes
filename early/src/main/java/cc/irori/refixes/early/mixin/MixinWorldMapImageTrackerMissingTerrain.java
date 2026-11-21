package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.ExistingTerrainMap;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.hypixel.hytale.server.core.universe.world.WorldMapImageTracker")
public abstract class MixinWorldMapImageTrackerMissingTerrain {

    @Shadow
    @Final
    private LongSet reloads;

    @Shadow
    @Final
    private Long2ObjectOpenHashMap<CompletableFuture<MapImage>> requests;

    @Unique
    private ExistingTerrainMap refixes$terrain;

    @Unique
    private final LongOpenHashSet refixes$requested = new LongOpenHashSet();

    @Inject(method = "requestedIndices", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void refixes$skipTerrainProbes(CallbackInfoReturnable<LongSet> cir) {
        if (refixes$terrain == null) return;
        LongOpenHashSet requested = refixes$requested;
        requested.clear();
        ObjectIterator<Long2ObjectMap.Entry<CompletableFuture<MapImage>>> iterator =
                requests.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<CompletableFuture<MapImage>> entry = iterator.next();
            if (!ExistingTerrainMap.isProbing(entry.getValue())) requested.add(entry.getLongKey());
        }
        cir.setReturnValue(requested);
    }

    @Inject(method = "processReloads", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$holdMissingReloads(
            int maxRequests,
            LongFunction<CompletableFuture<MapImage>> requestImage,
            @Coerce Object deliver,
            CallbackInfo ci) {
        if (!(requestImage instanceof ExistingTerrainMap terrain)) return;
        refixes$terrain = terrain;
        reloads.removeIf(terrain::isWaiting);
    }

    @Inject(
            method = "pollImage",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/WorldMapImageTracker;recordFailure(J)V"),
            cancellable = true,
            require = 2,
            allow = 2)
    private void refixes$keepMissingTerrain(
            long index,
            LongFunction<CompletableFuture<MapImage>> requestImage,
            CallbackInfoReturnable<MapImage> cir,
            @Local CompletableFuture<MapImage> future) {
        if (!(requestImage instanceof ExistingTerrainMap terrain) || !ExistingTerrainMap.isMissing(future)) return;
        terrain.settleMissing(index, future);
        cir.setReturnValue(null);
    }

    @Inject(method = "processReadyImages", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$settleMissingTerrain(CallbackInfo ci) {
        ExistingTerrainMap terrain = refixes$terrain;
        if (terrain == null) return;
        ObjectIterator<Long2ObjectMap.Entry<CompletableFuture<MapImage>>> iterator =
                requests.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<CompletableFuture<MapImage>> entry = iterator.next();
            long index = entry.getLongKey();
            CompletableFuture<MapImage> future = entry.getValue();
            if (reloads.contains(index) || !ExistingTerrainMap.isMissing(future)) continue;
            iterator.remove();
            terrain.settleMissing(index, future);
        }
    }

    @Inject(method = "isReloadPending", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void refixes$skipMissingTerrain(long index, CallbackInfoReturnable<Boolean> cir) {
        ExistingTerrainMap terrain = refixes$terrain;
        if (terrain != null && terrain.holdsBack(index)) cir.setReturnValue(true);
    }

    @Inject(method = "queueReloads", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$forgetReloadedTerrain(LongSet indices, boolean onlyTracked, CallbackInfo ci) {
        if (refixes$terrain != null) refixes$terrain.forget(indices);
    }

    @Inject(method = "unloadOutside", at = @At("TAIL"), require = 1, allow = 1)
    private void refixes$forgetHiddenTerrain(LongPredicate visible, LongConsumer unload, CallbackInfo ci) {
        if (refixes$terrain != null) refixes$terrain.retain(visible);
    }

    @Inject(method = "clear", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$forgetMissingTerrain(CallbackInfo ci) {
        if (refixes$terrain != null) refixes$terrain.clear();
    }
}
