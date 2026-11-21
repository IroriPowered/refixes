package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.ExistingTerrainMap;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntSupplier;
import java.util.function.LongFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WorldMapTracker.class)
public abstract class MixinWorldMapTrackerMissingTerrain {

    @Shadow
    @Final
    private PlayerRef playerRef;

    @Shadow
    private int lastChunkX;

    @Shadow
    private int lastChunkZ;

    @Unique
    private final ExistingTerrainMap refixes$terrain = new ExistingTerrainMap();

    @Unique
    private final IntSupplier refixes$viewRadius = () -> refixes$clientViewRadius();

    @ModifyArg(
            method = "loadImages",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/WorldMapTracker;scanImages(Lcom/hypixel/hytale/math/iterator/CircleSpiralIterator;Lcom/hypixel/hytale/server/core/universe/world/WorldMapImageTracker;ILjava/util/function/LongFunction;Lcom/hypixel/hytale/server/core/universe/world/WorldMapImageTracker$ImageDelivery;)Lcom/hypixel/hytale/server/core/universe/world/WorldMapTracker$ScanResult;"),
            index = 3,
            require = 1,
            allow = 1)
    private LongFunction<CompletableFuture<MapImage>> refixes$drawExistingTerrain(
            LongFunction<CompletableFuture<MapImage>> images, @Local(argsOnly = true) World world) {
        return refixes$terrain.use(world, images, lastChunkX, lastChunkZ, refixes$viewRadius);
    }

    @ModifyArg(
            method = "processPendingImages",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/WorldMapImageTracker;processReloads(ILjava/util/function/LongFunction;Lcom/hypixel/hytale/server/core/universe/world/WorldMapImageTracker$ImageDelivery;)V"),
            index = 1,
            require = 1,
            allow = 1)
    private LongFunction<CompletableFuture<MapImage>> refixes$reloadExistingTerrain(
            LongFunction<CompletableFuture<MapImage>> images, @Local(argsOnly = true) World world) {
        return refixes$terrain
                .use(world, images, lastChunkX, lastChunkZ, refixes$viewRadius)
                .recheck();
    }

    @Unique
    private int refixes$clientViewRadius() {
        Player player = playerRef.getComponentConcurrent(Player.getComponentType());
        return player != null ? player.getViewRadius() : Player.DEFAULT_VIEW_RADIUS_CHUNKS;
    }
}
