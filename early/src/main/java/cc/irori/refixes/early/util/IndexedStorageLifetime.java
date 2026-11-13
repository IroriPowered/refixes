package cc.irori.refixes.early.util;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public final class IndexedStorageLifetime {
    private static final ThreadLocal<IndexedStorageLifetime> CHILD = new ThreadLocal<>();
    private int active;
    private boolean closing;
    private CompletableFuture<Void> idle = CompletableFuture.completedFuture(null);
    private final CompletableFuture<Void> closure = new CompletableFuture<>();

    public interface Access<T> {
        T run() throws IOException;
    }

    private synchronized boolean begin() {
        if (closing && (CHILD.get() != this || active == 0)) return false;
        if (active++ == 0) idle = new CompletableFuture<>();
        return true;
    }

    private void end() {
        CompletableFuture<Void> completed;
        synchronized (this) {
            if (--active != 0) return;
            completed = idle;
        }
        completed.complete(null);
    }

    public <T> CompletableFuture<T> track(Supplier<CompletableFuture<T>> operation) {
        if (!begin()) return CompletableFuture.failedFuture(new IllegalStateException("Indexed storage is closed"));
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            Objects.requireNonNull(operation.get()).whenComplete((value, error) -> {
                end();
                if (error == null) result.complete(value);
                else result.completeExceptionally(error);
            });
        } catch (Throwable error) {
            end();
            result.completeExceptionally(error);
        }
        return result;
    }

    public <T> T access(Access<T> operation) throws IOException {
        if (!begin()) throw new IOException("Indexed storage is closed");
        try {
            return operation.run();
        } finally {
            end();
        }
    }

    public synchronized CompletableFuture<Void> beginClose() {
        closing = true;
        return idle.copy();
    }

    public CompletableFuture<Void> closedFuture() {
        return closure.copy();
    }

    public <T> CompletableFuture<T> child(Supplier<CompletableFuture<T>> operation) {
        IndexedStorageLifetime previous = CHILD.get();
        CHILD.set(this);
        try {
            return operation.get();
        } finally {
            if (previous == null) CHILD.remove();
            else CHILD.set(previous);
        }
    }

    public void closed(boolean success) {
        if (success) closure.complete(null);
        else closure.completeExceptionally(new IOException("Indexed storage did not close successfully"));
    }

    public static <T> T await(CompletableFuture<T> future) throws IOException {
        try {
            return future.join();
        } catch (CompletionException failure) {
            Throwable error = failure;
            while ((error instanceof CompletionException || error instanceof ExecutionException)
                    && error.getCause() != null) error = error.getCause();
            if (error instanceof IOException io) throw io;
            if (error instanceof RuntimeException runtime) throw runtime;
            if (error instanceof Error fatal) throw fatal;
            throw new IOException(error);
        }
    }
}
