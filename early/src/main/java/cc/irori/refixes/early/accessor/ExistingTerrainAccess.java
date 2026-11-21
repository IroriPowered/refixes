package cc.irori.refixes.early.accessor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.concurrent.CompletableFuture;

public interface ExistingTerrainAccess {

    CompletableFuture<Ref<ChunkStore>> refixes$getExistingChunk(long index);
}
