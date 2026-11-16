package cc.irori.refixes.early.util;

import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.worldgen.IWorldGen;

public final class WorldHeight {

    private WorldHeight() {}

    public static boolean isUnbounded(ChunkStore store, boolean cubicStorage) {
        return cubicStorage && store.getGenerator() instanceof IWorldGen.Cubic;
    }
}
