package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.IndexedCacheLifetime;
import cc.irori.refixes.early.util.IndexedStorageLifetime;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.storage.BufferChunkSaver;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.provider.IndexedStorageChunkStorageProvider;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(IndexedStorageChunkStorageProvider.IndexedStorageChunkSaver.class)
public abstract class MixinIndexedColumnSaveOrder extends BufferChunkSaver {

    @Shadow
    @Final
    private IndexedStorageChunkStorageProvider.IndexedStorageCache cache;

    @Unique
    private IndexedStorageLifetime refixes$lifetime() {
        return ((IndexedCacheLifetime) cache).refixes$getLifetime();
    }

    @Unique
    private static final ThreadLocal<Boolean> refixes$ORDERED = new ThreadLocal<>();

    @Unique
    private final ConcurrentHashMap<Long, CompletableFuture<Void>> refixes$columnWrites = new ConcurrentHashMap<>();

    protected MixinIndexedColumnSaveOrder(Store<ChunkStore> store) {
        super(store);
    }

    @Override
    public CompletableFuture<Void> saveChunkColumn(
            int x,
            int z,
            Store<ChunkStore> store,
            Ref<ChunkStore> ref,
            Executor completionExecutor,
            Runnable onIoComplete) {
        java.util.concurrent.atomic.AtomicBoolean notified = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable completed = () -> {
            if (notified.compareAndSet(false, true) && onIoComplete != null) onIoComplete.run();
        };
        return refixes$lifetime()
                .track(() -> refixes$orderedColumn(x, z, store, ref, completed))
                .whenComplete((ignored, error) -> {
                    if (error != null) completed.run();
                });
    }

    @Unique
    private CompletableFuture<Void> refixes$orderedColumn(
            int x, int z, Store<ChunkStore> store, Ref<ChunkStore> ref, Runnable onIoComplete) {
        Holder<ChunkStore> holder = store.copySerializableEntity(ref);
        CompletableFuture<Void> future = new CompletableFuture<>();
        refixes$inColumnOrder(
                        x,
                        z,
                        () -> refixes$lifetime().child(() -> refixes$ordered(() -> this.saveHolder(x, z, holder))))
                .whenComplete((v, t) -> {
                    try {
                        if (onIoComplete != null) onIoComplete.run();
                    } finally {
                        if (t == null) {
                            future.complete(null);
                        } else {
                            future.completeExceptionally(t);
                        }
                    }
                });
        return future;
    }

    @Unique
    private CompletableFuture<Void> refixes$inColumnOrder(int x, int z, Supplier<CompletableFuture<Void>> write) {
        Long column = ChunkUtil.indexChunk(x, z);
        CompletableFuture<Void> written = new CompletableFuture<>();
        CompletableFuture<Void> previous = refixes$columnWrites.put(column, written);
        return (previous == null ? CompletableFuture.<Void>completedFuture(null) : previous)
                .thenComposeAsync(ignored -> write.get())
                .whenComplete((v, t) -> {
                    refixes$columnWrites.remove(column, written);
                    written.complete(null);
                });
    }

    @Unique
    private static CompletableFuture<Void> refixes$ordered(Supplier<CompletableFuture<Void>> write) {
        Boolean previous = refixes$ORDERED.get();
        refixes$ORDERED.set(true);
        try {
            return write.get();
        } finally {
            if (previous == null) refixes$ORDERED.remove();
            else refixes$ORDERED.set(previous);
        }
    }

    @WrapMethod(method = "saveBuffer")
    private CompletableFuture<Void> refixes$bufferWrite(
            int x, int z, ByteBuffer buffer, Operation<CompletableFuture<Void>> original) {
        return refixes$lifetime()
                .track(() -> Boolean.TRUE.equals(refixes$ORDERED.get())
                        ? original.call(x, z, buffer)
                        : refixes$inColumnOrder(x, z, () -> original.call(x, z, buffer)));
    }

    @WrapMethod(method = "removeBuffer")
    private CompletableFuture<Void> refixes$bufferRemoval(int x, int z, Operation<CompletableFuture<Void>> original) {
        return refixes$lifetime().track(() -> refixes$inColumnOrder(x, z, () -> original.call(x, z)));
    }

    @WrapMethod(method = "compact")
    private CompletableFuture<Void> refixes$compaction(long[] removed, Operation<CompletableFuture<Void>> original) {
        return refixes$lifetime().track(() -> original.call(removed));
    }
}
