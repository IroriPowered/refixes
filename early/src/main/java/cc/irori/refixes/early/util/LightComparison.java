package cc.irori.refixes.early.util;

import cc.irori.refixes.early.accessor.ChunkLightDataAccess;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

public final class LightComparison {

    private LightComparison() {}

    public static boolean same(ChunkLightData a, ChunkLightData b) {
        MemorySegment left = ((ChunkLightDataAccess) a).refixes$getLightData();
        MemorySegment right = ((ChunkLightDataAccess) b).refixes$getLightData();
        if (left == null) return right == null || uniform(right, 0, (short) 0);
        if (right == null) return uniform(left, 0, (short) 0);
        return same(left, 0, right, 0);
    }

    private static boolean same(MemorySegment a, int nodeA, MemorySegment b, int nodeB) {
        long baseA = (long) nodeA * ChunkLightData.SIZE_MAGIC;
        long baseB = (long) nodeB * ChunkLightData.SIZE_MAGIC;
        int maskA = a.get(ValueLayout.JAVA_BYTE, baseA);
        int maskB = b.get(ValueLayout.JAVA_BYTE, baseB);
        for (int i = 0; i < ChunkLightData.TREE_SIZE; i++) {
            short valueA = a.get(ValueLayout.JAVA_SHORT_UNALIGNED, baseA + 1 + 2L * i);
            short valueB = b.get(ValueLayout.JAVA_SHORT_UNALIGNED, baseB + 1 + 2L * i);
            boolean branchA = ((maskA >> i) & 1) == 1;
            boolean branchB = ((maskB >> i) & 1) == 1;
            if (branchA && branchB) {
                if (!same(a, Short.toUnsignedInt(valueA), b, Short.toUnsignedInt(valueB))) return false;
            } else if (branchA) {
                if (!uniform(a, Short.toUnsignedInt(valueA), valueB)) return false;
            } else if (branchB) {
                if (!uniform(b, Short.toUnsignedInt(valueB), valueA)) return false;
            } else if (valueA != valueB) {
                return false;
            }
        }
        return true;
    }

    private static boolean uniform(MemorySegment tree, int node, short value) {
        long base = (long) node * ChunkLightData.SIZE_MAGIC;
        int mask = tree.get(ValueLayout.JAVA_BYTE, base);
        for (int i = 0; i < ChunkLightData.TREE_SIZE; i++) {
            short child = tree.get(ValueLayout.JAVA_SHORT_UNALIGNED, base + 1 + 2L * i);
            if (((mask >> i) & 1) == 1) {
                if (!uniform(tree, Short.toUnsignedInt(child), value)) return false;
            } else if (child != value) {
                return false;
            }
        }
        return true;
    }
}
