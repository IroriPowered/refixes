package cc.irori.refixes.early.accessor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public interface SharedInstanceResetAccess {

    boolean refixes$isVisited();

    void refixes$setVisited(boolean visited);

    boolean refixes$isResetArmed();

    void refixes$setResetArmed(boolean armed);

    AtomicReference<CompletableFuture<Void>> refixes$resetGate();

    AtomicReference<CompletableFuture<Void>> refixes$cleanupGate();

    boolean refixes$isShutDown();
}
