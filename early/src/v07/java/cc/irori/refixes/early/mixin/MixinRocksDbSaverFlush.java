package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.RocksDbFlush;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider.RocksDbResource;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.foreign.MemorySegment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(RocksDbChunkStorageProvider.Saver.class)
public abstract class MixinRocksDbSaverFlush {
    @Shadow
    @Final
    private RocksDbResource db;

    @WrapOperation(
            method = "flush()V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/lib/rocksdb/RocksDbNative;flush(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;)V"),
            require = 1,
            allow = 1)
    private void refixes$flushDataColumns(
            MemorySegment database, MemorySegment options, MemorySegment error, Operation<Void> original) {
        RocksDbFlush.flush(db, error);
    }
}
