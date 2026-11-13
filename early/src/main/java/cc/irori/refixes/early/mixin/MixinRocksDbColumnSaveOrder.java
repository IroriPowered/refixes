package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RocksDbChunkStorageProvider.Saver.class)
public class MixinRocksDbColumnSaveOrder {

    @Unique
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> refixes$columnWrites = new ConcurrentHashMap<>();

    @WrapOperation(
            method = {
                "saveChunkColumn(IILcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/component/Ref;Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;",
                "saveHolder(IILcom/hypixel/hytale/component/Holder;)Ljava/util/concurrent/CompletableFuture;"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/provider/RocksDbChunkStorageProvider$Saver;submitChunkColumnBatch(IILcom/hypixel/hytale/component/Holder;Ljava/util/List;Ljava/util/List;Ljava/util/List;)Ljava/util/concurrent/CompletableFuture;"),
            require = 2,
            allow = 2)
    private CompletableFuture<Void> refixes$orderColumnWrite(
            RocksDbChunkStorageProvider.Saver saver,
            int x,
            int z,
            Holder<ChunkStore> holder,
            List<?> sections,
            List<?> entities,
            List<?> heightmap,
            Operation<CompletableFuture<Void>> original) {
        Long column = ChunkUtil.indexChunk(x, z);
        CompletableFuture<Void> written = new CompletableFuture<>();
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture<Void> previous = refixes$columnWrites.put(column, written);
        if (previous == null) {
            CompletableFuture<Void> io;
            try {
                io = original.call(saver, x, z, holder, sections, entities, heightmap);
            } catch (Throwable t) {
                refixes$release(column, written);
                throw t;
            }
            refixes$forward(column, written, io, result);
            return result;
        }
        previous.whenCompleteAsync((ignored, failure) -> {
            CompletableFuture<Void> io;
            try {
                io = original.call(saver, x, z, holder, sections, entities, heightmap);
            } catch (Throwable t) {
                io = CompletableFuture.failedFuture(t);
            }
            refixes$forward(column, written, io, result);
        });
        return result;
    }

    @Unique
    private void refixes$forward(
            Long column, CompletableFuture<Void> written, CompletableFuture<Void> io, CompletableFuture<Void> result) {
        io.whenComplete((v, t) -> {
            refixes$release(column, written);
            if (t == null) {
                result.complete(null);
            } else {
                result.completeExceptionally(t);
            }
        });
    }

    @Unique
    private void refixes$release(Long column, CompletableFuture<Void> written) {
        refixes$columnWrites.remove(column, written);
        written.complete(null);
    }
}
