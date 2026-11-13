package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.IndexedCacheLifetime;
import cc.irori.refixes.early.util.IndexedStorageLifetime;
import com.hypixel.hytale.server.core.universe.world.storage.provider.IndexedStorageChunkStorageProvider.IndexedStorageCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(IndexedStorageCache.class)
public abstract class MixinIndexedCacheLifetime implements IndexedCacheLifetime {
    @Unique
    private final IndexedStorageLifetime refixes$lifetime = new IndexedStorageLifetime();

    @Unique
    private final ThreadLocal<Boolean> refixes$asynchronous = new ThreadLocal<>();

    @Unique
    private CompletableFuture<Void> refixes$closing;

    @Override
    public IndexedStorageLifetime refixes$getLifetime() {
        return refixes$lifetime;
    }

    @Override
    public CompletableFuture<Void> refixes$closeAsync() {
        Boolean previous = refixes$asynchronous.get();
        refixes$asynchronous.set(true);
        try {
            ((IndexedStorageCache) (Object) this).close();
            synchronized (this) {
                return refixes$closing.copy();
            }
        } catch (IOException | RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        } finally {
            if (previous == null) refixes$asynchronous.remove();
            else refixes$asynchronous.set(previous);
        }
    }

    @Unique
    private synchronized CompletableFuture<Void> refixes$startClose(Operation<Void> original) {
        if (refixes$closing == null) {
            refixes$closing = refixes$lifetime.beginClose().thenRun(() -> original.call());
            refixes$closing.whenComplete((ignored, error) -> refixes$lifetime.closed(error == null));
        }
        return refixes$closing.copy();
    }

    @WrapMethod(method = "getIndexes")
    private LongList refixes$indexes(Operation<LongList> original) throws IOException {
        return refixes$lifetime.access(() -> original.call());
    }

    @WrapMethod(method = "flush")
    private void refixes$flush(Operation<Void> original) throws IOException {
        refixes$lifetime.access(() -> original.call());
    }

    @WrapMethod(method = "close")
    private void refixes$closed(Operation<Void> original) throws IOException {
        CompletableFuture<Void> closing = refixes$startClose(original);
        if (!Boolean.TRUE.equals(refixes$asynchronous.get())) IndexedStorageLifetime.await(closing);
    }
}
