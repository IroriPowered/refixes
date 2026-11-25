package cc.irori.refixes.early.accessor;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.StampedLock;

@IfHytale("<0.8.0-pre.1")
public interface LoadStateAccess {

    StampedLock refixes$getLock();

    int refixes$getFlags();

    CompletableFuture<Ref<ChunkStore>> refixes$getFuture();

    Ref<ChunkStore> refixes$getReference();
}
