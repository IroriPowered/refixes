package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(WorldMapTracker.class)
public abstract class MixinWorldMapPacing {

    @WrapOperation(
            method = {"loadImages", "loadWorldMap"},
            at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", ordinal = 1),
            require = 2,
            allow = 2)
    private boolean refixes$limitReadyImages(
            List<?> batches,
            Object batch,
            Operation<Boolean> original,
            @Local(name = "maxGeneration") LocalIntRef remaining) {
        boolean added = original.call(batches, batch);
        remaining.set(0);
        return added;
    }

    @WrapOperation(
            method = "processPendingReloadChunks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/WorldMapTracker;writeUpdatePacket(Ljava/util/List;)V",
                            ordinal = 0),
            require = 1,
            allow = 1)
    private void refixes$finishReloadBatch(
            WorldMapTracker tracker,
            List<MapChunk> chunks,
            Operation<Void> original,
            @Share("refixes$reloadBatchSent") LocalBooleanRef sent) {
        original.call(tracker, chunks);
        sent.set(true);
    }

    @WrapOperation(
            method = "processPendingReloadChunks",
            at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/LongIterator;hasNext()Z"),
            require = 1,
            allow = 1)
    private boolean refixes$limitReloadImages(
            LongIterator iterator,
            Operation<Boolean> original,
            @Share("refixes$reloadBatchSent") LocalBooleanRef sent) {
        return !sent.get() && original.call(iterator);
    }

    @WrapOperation(
            method = {"loadImages", "loadWorldMap", "processPendingReloadChunks"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/WorldMapTracker;writeUpdatePacket(Ljava/util/List;)V",
                            ordinal = 1),
            require = 3,
            allow = 3)
    private void refixes$skipEmptyRemainder(WorldMapTracker tracker, List<MapChunk> chunks, Operation<Void> original) {
        if (chunks != null && chunks.isEmpty()) return;
        original.call(tracker, chunks);
    }
}
