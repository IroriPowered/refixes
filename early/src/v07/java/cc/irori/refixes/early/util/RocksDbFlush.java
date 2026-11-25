package cc.irori.refixes.early.util;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.lib.rocksdb.RocksDbNative;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider.RocksDbException;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider.RocksDbResource;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

@IfHytale("<0.8.0-pre.1")
public final class RocksDbFlush {
    private RocksDbFlush() {}

    public static void flush(RocksDbResource resource) {
        try (var arena = Arena.ofConfined()) {
            var error = arena.allocate(RocksDbNative.C_POINTER);
            error.set(RocksDbNative.C_POINTER, 0, MemorySegment.NULL);
            flush(resource, error);
            String message = RocksDbNative.consumeError(error);
            if (message != null) throw new RocksDbException(message);
        }
    }

    public static void flush(RocksDbResource resource, MemorySegment error) {
        MemorySegment[] columns = {
            resource.chunkColumn,
            resource.sectionColumn,
            resource.entitySectionColumn,
            resource.entityColumn,
            resource.blockComponentColumn,
            resource.heightmapNodeColumn,
            resource.heightmapInternalColumn
        };
        try (var arena = Arena.ofConfined()) {
            var handles = arena.allocate(RocksDbNative.C_POINTER, columns.length);
            for (int i = 0; i < columns.length; i++) {
                handles.setAtIndex(RocksDbNative.C_POINTER, i, columns[i]);
            }
            RocksDbNative.flushCfs(resource.db, resource.flushOptions, handles, columns.length, error);
        }
    }
}
