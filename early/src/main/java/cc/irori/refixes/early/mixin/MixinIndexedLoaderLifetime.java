package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.IndexedCacheLifetime;
import com.hypixel.hytale.server.core.universe.world.storage.provider.IndexedStorageChunkStorageProvider.IndexedStorageCache;
import com.hypixel.hytale.server.core.universe.world.storage.provider.IndexedStorageChunkStorageProvider.IndexedStorageChunkLoader;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(IndexedStorageChunkLoader.class)
public abstract class MixinIndexedLoaderLifetime {
    @Shadow
    @Final
    private IndexedStorageCache cache;

    @WrapMethod(method = "loadBuffer")
    private CompletableFuture<ByteBuffer> refixes$trackRead(
            int x, int z, Operation<CompletableFuture<ByteBuffer>> original) {
        return ((IndexedCacheLifetime) cache).refixes$getLifetime().track(() -> original.call(x, z));
    }
}
