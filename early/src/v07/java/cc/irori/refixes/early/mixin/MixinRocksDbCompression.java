package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.lib.rocksdb.RocksDbNative;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfHytale("<0.8.0-pre.1")
@Mixin(RocksDbChunkStorageProvider.class)
public abstract class MixinRocksDbCompression {
    @Unique
    private static final long refixes$maxTotalWalSize = refixes$walSize();

    @Unique
    private static long refixes$walSize() {
        String value = System.getProperty("hytale.rocksdb.max_total_wal_size");
        return value == null || value.isBlank() ? 128L * 1024 * 1024 : Long.parseLong(value.trim());
    }

    @WrapOperation(
            method =
                    "initialize(Lcom/hypixel/hytale/component/Store;Z)Lcom/hypixel/hytale/server/core/universe/world/storage/provider/RocksDbChunkStorageProvider$RocksDbResource;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/lib/rocksdb/RocksDbNative;optionsSetMaxTotalWalSize(Ljava/lang/foreign/MemorySegment;J)V"),
            require = 1,
            allow = 1)
    private void refixes$compressWal(MemorySegment options, long size, Operation<Void> original) {
        original.call(options, refixes$maxTotalWalSize);
        RocksDbNative.optionsSetWalCompression(options, RocksDbNative.COMPRESSION_ZSTD);
    }

    @Inject(method = "buildColumnOptions", at = @At("RETURN"), require = 1, allow = 1)
    private static void refixes$compressAllLevels(
            MemorySegment blockCache,
            MemorySegment blobCache,
            long budget,
            int prefixLength,
            boolean blobFiles,
            CallbackInfoReturnable<MemorySegment> ci) {
        MemorySegment options = ci.getReturnValue();
        int levels = RocksDbNative.optionsGetNumLevels(options);
        try (var arena = Arena.ofConfined()) {
            var compression = arena.allocate(ValueLayout.JAVA_INT, levels);
            for (int i = 0; i < levels; i++) {
                compression.setAtIndex(ValueLayout.JAVA_INT, i, RocksDbNative.COMPRESSION_ZSTD);
            }
            RocksDbNative.optionsSetCompressionPerLevel(options, compression, levels);
        }
    }
}
