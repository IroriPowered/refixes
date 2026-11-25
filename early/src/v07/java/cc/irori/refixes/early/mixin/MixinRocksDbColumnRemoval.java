package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.lib.rocksdb.RocksDbNative;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider.RocksDbResource;
import com.hypixel.hytale.server.core.util.io.MemorySegmentUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.logging.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(RocksDbChunkStorageProvider.Saver.class)
public abstract class MixinRocksDbColumnRemoval {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final int refixes$CHUNK_KEY_BYTES = 8;

    @Unique
    private static final int refixes$SECTION_KEY_BYTES = 12;

    @Unique
    private static final int refixes$ENTITY_KEY_BYTES = 16;

    @Unique
    private static final int refixes$COLUMN_RANGE_END_KEY_MAX_BYTES = 15;

    @Unique
    private static final long refixes$MAX_ENTITIES_PER_SECTION = 1 << 16;

    @Shadow
    @Final
    private RocksDbResource db;

    @WrapOperation(
            method = "lambda$removeHolder$0(II)V",
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lcom/hypixel/hytale/lib/rocksdb/RocksDbNative;writebatchDeleteRangeCf(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;JLjava/lang/foreign/MemorySegment;J)V",
                        ordinal = 0),
                @At(
                        value = "INVOKE",
                        target =
                                "Lcom/hypixel/hytale/lib/rocksdb/RocksDbNative;writebatchDeleteRangeCf(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;JLjava/lang/foreign/MemorySegment;J)V",
                        ordinal = 2)
            },
            require = 2,
            allow = 2)
    private void refixes$deleteColumnRange(
            MemorySegment batch,
            MemorySegment cf,
            MemorySegment begin,
            long beginLength,
            MemorySegment end,
            long endLength,
            Operation<Void> original,
            @Local(argsOnly = true, ordinal = 0) int x,
            @Local(argsOnly = true, ordinal = 1) int z) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rangeStart = refixes$columnKey(arena, x, z);
            MemorySegment rangeEnd = refixes$columnRangeEndKey(arena, x, z);
            original.call(batch, cf, rangeStart, rangeStart.byteSize(), rangeEnd, rangeEnd.byteSize());
        }
    }

    @WrapOperation(
            method = "lambda$removeHolder$0(II)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/lib/rocksdb/RocksDbNative;writebatchDeleteRangeCf(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;JLjava/lang/foreign/MemorySegment;J)V",
                            ordinal = 1),
            require = 1,
            allow = 1)
    private void refixes$deleteColumnEntitySections(
            MemorySegment batch,
            MemorySegment cf,
            MemorySegment begin,
            long beginLength,
            MemorySegment end,
            long endLength,
            Operation<Void> original,
            @Local(argsOnly = true, ordinal = 0) int x,
            @Local(argsOnly = true, ordinal = 1) int z) {
        try (Arena arena = Arena.ofConfined()) {
            refixes$deleteColumnEntities(
                    batch, x, z, refixes$columnKey(arena, x, z), refixes$columnRangeEndKey(arena, x, z));
        }
    }

    @Unique
    private static MemorySegment refixes$columnKey(Arena arena, int x, int z) {
        MemorySegment key = arena.allocate(refixes$CHUNK_KEY_BYTES);
        key.set(MemorySegmentUtil.INT_BE, 0, x);
        key.set(MemorySegmentUtil.INT_BE, 4, z);
        return key;
    }

    @Unique
    private static MemorySegment refixes$columnRangeEndKey(Arena arena, int x, int z) {
        if (x == -1 && z == -1) {
            MemorySegment key = arena.allocate(refixes$COLUMN_RANGE_END_KEY_MAX_BYTES);
            key.fill((byte) 0xFF);
            return key;
        }
        MemorySegment key = arena.allocate(refixes$CHUNK_KEY_BYTES);
        key.set(MemorySegmentUtil.LONG_BE, 0, (((long) x << 32) | (z & 0xFFFFFFFFL)) + 1L);
        return key;
    }

    @Unique
    private void refixes$deleteColumnEntities(
            MemorySegment batch, int x, int z, MemorySegment rangeStart, MemorySegment rangeEnd) {
        MemorySegment readOptions = RocksDbNative.readoptionsCreate();
        try (Arena arena = Arena.ofConfined()) {
            RocksDbNative.readoptionsSetTotalOrderSeek(readOptions, true);
            RocksDbNative.readoptionsSetIterateUpperBound(readOptions, rangeEnd, rangeEnd.byteSize());
            MemorySegment keyLen = arena.allocate(RocksDbNative.C_SIZE_T);
            MemorySegment valLen = arena.allocate(RocksDbNative.C_SIZE_T);
            MemorySegment iter = RocksDbNative.createIteratorCf(db.db, readOptions, db.entitySectionColumn);
            try {
                RocksDbNative.iterSeek(iter, rangeStart, rangeStart.byteSize());
                while (RocksDbNative.iterValid(iter)) {
                    MemorySegment keyPtr = RocksDbNative.iterKey(iter, keyLen);
                    long klen = RocksDbNative.getSizeT(keyLen, 0);
                    MemorySegment key = keyPtr.reinterpret(klen);
                    MemorySegment valPtr = RocksDbNative.iterValue(iter, valLen);
                    long vlen = RocksDbNative.getSizeT(valLen, 0);
                    if (klen != refixes$SECTION_KEY_BYTES
                            || vlen % refixes$ENTITY_KEY_BYTES != 0
                            || vlen / refixes$ENTITY_KEY_BYTES > refixes$MAX_ENTITIES_PER_SECTION) {
                        refixes$LOGGER.at(Level.WARNING).log(
                                "Leaving behind the entity rows of a corrupted entity_sections row in removed column (%d, %d): %d-byte key, %d-byte value",
                                x, z, klen, vlen);
                    } else {
                        MemorySegment packed = valPtr.reinterpret(vlen);
                        for (long offset = 0; offset < vlen; offset += refixes$ENTITY_KEY_BYTES) {
                            RocksDbNative.writebatchDeleteCf(
                                    batch,
                                    db.entityColumn,
                                    packed.asSlice(offset, refixes$ENTITY_KEY_BYTES),
                                    refixes$ENTITY_KEY_BYTES);
                        }
                    }
                    RocksDbNative.writebatchDeleteCf(batch, db.entitySectionColumn, key, klen);
                    RocksDbNative.iterNext(iter);
                }
                MemorySegment errPtr = arena.allocate(RocksDbNative.C_POINTER);
                RocksDbNative.iterGetError(iter, errPtr);
                String error = RocksDbNative.consumeError(errPtr);
                if (error != null) {
                    throw new RocksDbChunkStorageProvider.RocksDbException(error);
                }
            } finally {
                RocksDbNative.iterDestroy(iter);
            }
        } finally {
            RocksDbNative.readoptionsDestroy(readOptions);
        }
    }
}
