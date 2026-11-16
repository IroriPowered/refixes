package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.ExistingTerrainAccess;
import cc.irori.refixes.early.accessor.LoadStateAccess;
import com.hypixel.fastutil.longs.Long2ObjectConcurrentHashMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.StampedLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkGrid.class)
public abstract class MixinChunkGridExistingTerrain implements ExistingTerrainAccess {

    @Shadow
    @Final
    protected World world;

    @Shadow
    @Final
    protected Long2ObjectConcurrentHashMap<?> chunks;

    @Shadow
    protected Store<ChunkStore> store;

    @Shadow
    public abstract CompletableFuture<Ref<ChunkStore>> getChunkReferenceAsync(long index, int flags);

    @Override
    public CompletableFuture<Ref<ChunkStore>> refixes$getExistingChunk(long index) {
        CompletableFuture<Ref<ChunkStore>> future = getChunkReferenceAsync(index, GetChunkFlags.NO_GENERATE);
        future.thenAccept(reference -> {
            if (reference != null || store.isShutdown()) return;
            if (world.isInThread()) {
                refixes$forgetMissingChunk(index);
            } else if (world.isAlive()) {
                world.execute(() -> refixes$forgetMissingChunk(index));
            }
        });
        return future;
    }

    @Unique
    private void refixes$forgetMissingChunk(long index) {
        Object state = chunks.get(index);
        if (state == null) return;
        LoadStateAccess access = (LoadStateAccess) state;
        StampedLock lock = access.refixes$getLock();
        long stamp = lock.writeLock();
        try {
            CompletableFuture<Ref<ChunkStore>> future = access.refixes$getFuture();
            if (access.refixes$getReference() == null
                    && access.refixes$getFlags() == GetChunkFlags.NO_GENERATE
                    && future != null
                    && future.isDone()
                    && !future.isCompletedExceptionally()
                    && future.getNow(null) == null) {
                chunks.remove(index, state);
            }
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}
