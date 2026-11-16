package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.ExistingTerrainAccess;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.provider.chunk.ImageBuilder;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ImageBuilder.class)
public abstract class MixinImageBuilderExistingTerrain {

    @Shadow
    private World world;

    @Redirect(
            method = {"fetchChunk", "sampleNeighborsSync"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/ChunkStore;getChunkReferenceAsync(J)Ljava/util/concurrent/CompletableFuture;"),
            require = 9,
            allow = 9)
    private CompletableFuture<Ref<ChunkStore>> refixes$loadWithoutGenerating(ChunkStore store, long index) {
        if (world.getWorldMapManager().getWorldMapSettings().getWorldMapArea() != null) {
            return store.getChunkReferenceAsync(index);
        }
        return ((ExistingTerrainAccess) store).refixes$getExistingChunk(index);
    }
}
