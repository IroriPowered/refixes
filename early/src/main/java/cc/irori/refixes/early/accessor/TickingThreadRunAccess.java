package cc.irori.refixes.early.accessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public interface TickingThreadRunAccess {
    Thread refixes$getThread();

    CompletableFuture<Void> refixes$getStartedFuture();

    AtomicBoolean refixes$getNeedsShutdown();
}
