package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.common.fastutil.HLongSet;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldMapTracker.class)
public abstract class MixinWorldMapTracker {

    @Shadow
    @Final
    private HLongSet pendingReloadChunks;

    @Shadow
    @Final
    private Long2ObjectOpenHashMap<CompletableFuture<MapImage>> pendingReloadFutures;

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Inject(
            method = "clear",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/common/fastutil/HLongSet;clear()V"),
            require = 1,
            allow = 1)
    private void refixes$clearPendingReloads(CallbackInfo ci) {
        pendingReloadChunks.clear();
        pendingReloadFutures.clear();
    }

    @ModifyArg(
            method = "processPendingReloadChunks",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;<init>(I)V",
                            ordinal = 1),
            require = 1,
            allow = 1)
    private int refixes$fixReloadListCapacity(int capacity, @Local(index = 4) int fullMapChunkSize) {
        return (WorldMapTracker.MAX_FRAME - WorldMapTracker.EMPTY_UPDATE_WORLD_MAP_SIZE) / fullMapChunkSize;
    }

    @WrapMethod(method = "unloadImages")
    private void refixes$wrapUnloadImages(
            int chunkViewRadius, int playerChunkX, int playerChunkZ, Operation<Void> original) {
        try {
            original.call(chunkViewRadius, playerChunkX, playerChunkZ);
        } catch (NullPointerException e) {
            refixes$LOGGER.atWarning().withCause(e).log("WorldMapTracker#unloadImages(): Failed to run");
        }
    }
}
