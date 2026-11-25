package local.hyinit.verify;

import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class SharedInstanceStorageRegressionV07 extends SharedInstanceStorageRegression.RecordingSaver {

    @Override
    void assertRemovalsKeepStoredData(IChunkSaver.Cubic storage) {
        requireKept("removeEntity", storage.removeEntity(UUID.randomUUID()));
        requireKept("removeHolder", storage.removeHolder(1, 3));
    }

    @Override
    public CompletableFuture<Void> removeEntity(UUID uuid) {
        removeCalls.incrementAndGet();
        return CompletableFuture.completedFuture(null);
    }
}
