package cc.irori.refixes.early.duck;

import cc.irori.refixes.early.util.IndexedStorageLifetime;
import java.util.concurrent.CompletableFuture;

public interface IndexedCacheLifetime {
    IndexedStorageLifetime refixes$getLifetime();

    CompletableFuture<Void> refixes$closeAsync();
}
