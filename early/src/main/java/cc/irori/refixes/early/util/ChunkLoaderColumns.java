package cc.irori.refixes.early.util;

import java.util.Map;
import java.util.function.Function;

public final class ChunkLoaderColumns {
    private static volatile Function<String, Map<Long, String>> labels;

    private ChunkLoaderColumns() {}

    public static void set(Function<String, Map<Long, String>> labels) {
        ChunkLoaderColumns.labels = labels;
    }

    public static String label(String worldName, long index) {
        Function<String, Map<Long, String>> current = labels;
        return current == null ? null : current.apply(worldName).get(index);
    }
}
