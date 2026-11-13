package cc.irori.refixes.early.accessor;

import java.util.concurrent.CompletableFuture;

public interface TickingThreadRunAccess {
    Thread refixes$getThread();

    CompletableFuture<Void> refixes$getStartedFuture();
}
