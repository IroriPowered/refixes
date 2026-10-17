package cc.irori.refixes.early.accessor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.shorts.Short2ReferenceMap;

public interface BlockComponentSectionAccess {

    Short2ReferenceMap<Ref<ChunkStore>> refixes$getBlockReferences();
}
