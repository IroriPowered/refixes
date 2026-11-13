package cc.irori.refixes.early.duck;

import java.util.concurrent.CompletableFuture;

public interface QuicheDrainSignal {

    boolean refixes$backlogged();

    CompletableFuture<Void> refixes$drained();
}
