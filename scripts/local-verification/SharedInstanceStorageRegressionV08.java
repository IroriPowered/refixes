package local.hyinit.verify;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class SharedInstanceStorageRegressionV08 extends SharedInstanceStorageRegression.RecordingSaver {

    @Override
    void assertRemovalsKeepStoredData(IChunkSaver.Cubic storage) {
        UUID uuid = UUID.randomUUID();
        Object storageState = new Object();
        requireKept("removeSection", storage.removeSection(1, -2, 3));
        requireKept("removeEntity", storage.removeEntity(uuid, storageState));
        requireKept("removeEntity without a storage state", storage.removeEntity(uuid, null));
        requireKept("removeEntityMembership", storage.removeEntityMembership(uuid, storageState));
        requireKept("removeHolder", storage.removeHolder(1, 3));
    }

    @Override
    public CompletableFuture<Void> saveSectionHolder(int x, int y, int z, Holder<ChunkStore> holder) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeSection(int x, int y, int z) {
        removeCalls.incrementAndGet();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeEntity(UUID uuid, Object storageState) {
        removeCalls.incrementAndGet();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> removeEntityMembership(UUID uuid, Object storageState) {
        removeCalls.incrementAndGet();
        return CompletableFuture.completedFuture(null);
    }
}
