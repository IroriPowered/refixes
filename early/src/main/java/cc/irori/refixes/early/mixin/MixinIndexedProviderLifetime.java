package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.duck.IndexedCacheLifetime;
import cc.irori.refixes.early.duck.IndexedWorldOwner;
import cc.irori.refixes.early.util.IndexedStorageLifetime;
import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.RetiredWorlds;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.provider.IndexedStorageChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.storage.provider.IndexedStorageChunkStorageProvider.IndexedStorageCache;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(IndexedStorageChunkStorageProvider.class)
public abstract class MixinIndexedProviderLifetime {
    @WrapMethod(
            method =
                    "initialize(Lcom/hypixel/hytale/component/Store;Z)Lcom/hypixel/hytale/server/core/universe/world/storage/provider/IndexedStorageChunkStorageProvider$IndexedStorageCache;")
    private IndexedStorageCache refixes$registerCache(
            Store<ChunkStore> store, boolean readOnly, Operation<IndexedStorageCache> original) throws IOException {
        IndexedStorageCache cache = original.call(store, readOnly);
        var owner = (IndexedWorldOwner) store.getExternalData().getWorld();
        if (!owner.refixes$indexedStorage().register((IndexedCacheLifetime) cache)) {
            ((IndexedCacheLifetime) cache).refixes$closeAsync();
            throw new IOException("Indexed world storage is stopping");
        }
        return cache;
    }

    @WrapMethod(
            method =
                    "close(Lcom/hypixel/hytale/server/core/universe/world/storage/provider/IndexedStorageChunkStorageProvider$IndexedStorageCache;Lcom/hypixel/hytale/component/Store;)V")
    private void refixes$closeOwnedCache(IndexedStorageCache cache, Store<ChunkStore> store, Operation<Void> original)
            throws IOException {
        var owner = (IndexedWorldOwner) store.getExternalData().getWorld();
        if (!owner.refixes$indexedStorage().isStopping()) {
            original.call(cache, store);
            return;
        }
        CompletableFuture<Void> closing = ((IndexedCacheLifetime) cache).refixes$closeAsync();
        int timeout = EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.get();
        if (timeout <= 0
                || !EarlyOptions.isMixinEnabled("MixinStoreShutdown")
                || !HytaleServer.get().isShuttingDown()) {
            IndexedStorageLifetime.await(closing);
            return;
        }
        try {
            closing.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException error) {
            Logs.logger()
                    .atWarning()
                    .log(
                            "Indexed storage is still closing for world '%s'",
                            store.getExternalData().getWorld().getName());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while closing indexed world storage", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof IOException io) throw io;
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error fatal) throw fatal;
            throw new IOException("Could not close indexed world storage", cause);
        }
    }

    @WrapMethod(method = {"beginRecovery", "revertRecovery"})
    private void refixes$recoverAfterClose(Path file, Path recoveryPath, Operation<Void> original) throws IOException {
        IndexedStorageLifetime.await(RetiredWorlds.afterRelease(
                file.getFileName().toString(), file, () -> {
                    try {
                        original.call(file, recoveryPath);
                        return CompletableFuture.completedFuture(null);
                    } catch (Throwable error) {
                        return CompletableFuture.failedFuture(error);
                    }
                }));
    }
}
