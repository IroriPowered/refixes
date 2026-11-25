package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.lib.rocksdb.RocksDbNative;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider;
import com.hypixel.hytale.server.core.util.io.MemorySegmentUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(RocksDbChunkStorageProvider.class)
public abstract class MixinRocksDbScanBounds {

    @WrapOperation(
            method =
                    "scanHeightmapSkeleton(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;II)Lcom/hypixel/hytale/server/core/universe/world/storage/provider/RocksDbChunkStorageProvider$SkeletonScan;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/lib/rocksdb/RocksDbNative;createIteratorCf(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;)Ljava/lang/foreign/MemorySegment;"),
            require = 1,
            allow = 1)
    private static MemorySegment refixes$boundColumnScan(
            MemorySegment db,
            MemorySegment readOptions,
            MemorySegment cf,
            Operation<MemorySegment> original,
            @Local Arena arena,
            @Local(argsOnly = true, ordinal = 0) int x,
            @Local(argsOnly = true, ordinal = 1) int z) {
        MemorySegment bound;
        if (x == -1 && z == -1) {
            bound = arena.allocate(15);
            bound.fill((byte) 0xFF);
        } else {
            bound = arena.allocate(8);
            bound.set(MemorySegmentUtil.LONG_BE, 0, (((long) x << 32) | (z & 0xFFFFFFFFL)) + 1L);
        }
        RocksDbNative.readoptionsSetIterateUpperBound(readOptions, bound, bound.byteSize());
        return original.call(db, readOptions, cf);
    }
}
