package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.RocksDbFlush;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider.RocksDbResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfHytale("<0.8.0-pre.1")
@Mixin(RocksDbChunkStorageProvider.class)
public abstract class MixinRocksDbFlush {
    @Inject(
            method = "closeInternal",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/provider/RocksDbChunkStorageProvider;checkError(Ljava/lang/foreign/MemorySegment;)V",
                            shift = At.Shift.AFTER),
            require = 1,
            allow = 1)
    private void refixes$flushBeforeClose(RocksDbResource resource, CallbackInfoReturnable<Boolean> ci) {
        RocksDbFlush.flush(resource);
    }
}
