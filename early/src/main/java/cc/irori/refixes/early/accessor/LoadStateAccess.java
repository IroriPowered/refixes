package cc.irori.refixes.early.accessor;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.StampedLock;

public interface LoadStateAccess {

    StampedLock refixes$getLock();

    int refixes$getFlags();

    CompletableFuture<Ref<ChunkStore>> refixes$getFuture();

    Ref<ChunkStore> refixes$getReference();
}
