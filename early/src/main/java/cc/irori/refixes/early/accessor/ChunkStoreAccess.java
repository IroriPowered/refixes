package cc.irori.refixes.early.accessor;

import com.hypixel.hytale.server.core.universe.world.storage.IChunkLoader;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;

public interface ChunkStoreAccess {

    void refixes$setLoader(IChunkLoader loader);

    void refixes$setSaver(IChunkSaver saver);
}
