package cc.irori.refixes.early.accessor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.shorts.Short2ReferenceMap;

public interface BlockComponentSectionAccess {

    // World-thread-only inspection; callers must not mutate the live reference map.
    Short2ReferenceMap<Ref<ChunkStore>> refixes$getBlockReferences();
}
