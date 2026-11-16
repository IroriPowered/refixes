package cc.irori.refixes.early.util;

import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;

public final class WorldHeight {

    private WorldHeight() {}

    public static boolean isUnbounded(ChunkStore store, boolean cubicStorage) {
        boolean unbounded = cubicStorage && store.getGenerator() instanceof IWorldGen.Cubic;
        if (PatchCounters.ENABLED) PatchCounters.WORLD_HEIGHT_BOUNDS.countReached();
        if (PatchCounters.ENABLED && cubicStorage && !unbounded) PatchCounters.WORLD_HEIGHT_BOUNDS.countEffect();
        return unbounded;
    }
}
