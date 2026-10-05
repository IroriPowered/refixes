package cc.irori.refixes.early.accessor;

import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;

public interface ChunkStoreAccess {

    void refixes$setSaver(IChunkSaver saver);
}
