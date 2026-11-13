package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.lib.rocksdb.RocksDbNative;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RocksDbChunkStorageProvider.Loader.class)
public abstract class MixinRocksDbBlockComponentScanBounds {

    @Unique
    private static final int refixes$SECTION_KEY_BYTES = 12;

    @Unique
    private static final int refixes$SECTION_END_KEY_BYTES = 15;

    @WrapOperation(
            method = "loadBlockComponentRows(IIIILjava/util/function/IntFunction;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/lib/rocksdb/RocksDbNative;createIteratorCf(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;)Ljava/lang/foreign/MemorySegment;"),
            require = 1,
            allow = 1)
    private MemorySegment refixes$createBoundedIterator(
            MemorySegment db,
            MemorySegment sharedReadOptions,
            MemorySegment cf,
            Operation<MemorySegment> original,
            @Local Arena arena,
            @Share("refixes$readOptions") LocalRef<MemorySegment> readOptionsRef,
            @Share("refixes$bound") LocalRef<MemorySegment> boundRef) {
        MemorySegment readOptions = RocksDbNative.readoptionsCreate();
        try {
            MemorySegment bound = arena.allocate(refixes$SECTION_END_KEY_BYTES);
            RocksDbNative.readoptionsSetIterateUpperBound(readOptions, bound, refixes$SECTION_END_KEY_BYTES);
            MemorySegment iter = original.call(db, readOptions, cf);
            readOptionsRef.set(readOptions);
            boundRef.set(bound);
            return iter;
        } catch (Throwable t) {
            RocksDbNative.readoptionsDestroy(readOptions);
            throw t;
        }
    }

    @WrapOperation(
            method = "loadBlockComponentRows(IIIILjava/util/function/IntFunction;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/lib/rocksdb/RocksDbNative;iterSeek(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;J)V"),
            require = 1,
            allow = 1)
    private void refixes$boundSectionSeek(
            MemorySegment iter,
            MemorySegment seekKey,
            long seekLength,
            Operation<Void> original,
            @Share("refixes$bound") LocalRef<MemorySegment> boundRef) {
        MemorySegment bound = boundRef.get();
        MemorySegment.copy(seekKey, 0, bound, 0, refixes$SECTION_KEY_BYTES);
        bound.asSlice(refixes$SECTION_KEY_BYTES).fill((byte) 0xFF);
        original.call(iter, seekKey, seekLength);
    }

    @WrapOperation(
            method = "loadBlockComponentRows(IIIILjava/util/function/IntFunction;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/lib/rocksdb/RocksDbNative;iterDestroy(Ljava/lang/foreign/MemorySegment;)V"),
            require = 2,
            allow = 2)
    private void refixes$releaseReadOptions(
            MemorySegment iter,
            Operation<Void> original,
            @Share("refixes$readOptions") LocalRef<MemorySegment> readOptionsRef) {
        try {
            original.call(iter);
        } finally {
            MemorySegment readOptions = readOptionsRef.get();
            if (readOptions != null) {
                readOptionsRef.set(null);
                RocksDbNative.readoptionsDestroy(readOptions);
            }
        }
    }
}
