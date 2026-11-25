package cc.irori.refixes.early.accessor;

import cc.irori.hyinit.api.IfHytale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

@IfHytale("<0.8.0-pre.1")
public interface SharedInstanceCleanupAccess {

    AtomicReference<CompletableFuture<Void>> refixes$cleanupGate();

    boolean refixes$isShutDown();
}
