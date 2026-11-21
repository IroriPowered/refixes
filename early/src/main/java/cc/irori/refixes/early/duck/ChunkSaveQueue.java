package cc.irori.refixes.early.duck;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public interface ChunkSaveQueue {

    boolean refixes$enqueueLast(Ref<ChunkStore> reference);
}
