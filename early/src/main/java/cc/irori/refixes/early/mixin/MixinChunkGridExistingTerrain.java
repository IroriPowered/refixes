package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.ExistingTerrainAccess;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkGrid.class)
public abstract class MixinChunkGridExistingTerrain implements ExistingTerrainAccess {

    @Shadow
    public abstract CompletableFuture<Ref<ChunkStore>> getChunkReferenceAsync(long index, int flags);

    @Override
    public CompletableFuture<Ref<ChunkStore>> refixes$getExistingChunk(long index) {
        return getChunkReferenceAsync(index, GetChunkFlags.NO_GENERATE);
    }
}
