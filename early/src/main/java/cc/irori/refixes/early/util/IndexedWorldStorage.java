package cc.irori.refixes.early.util;

import cc.irori.refixes.early.duck.IndexedCacheLifetime;
import com.hypixel.hytale.server.core.universe.world.World;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class IndexedWorldStorage {
    private static final class Entry {
        final IndexedCacheLifetime cache;
        final CompletableFuture<Void> closed;
        boolean retained;

        Entry(IndexedCacheLifetime cache) {
            this.cache = cache;
            closed = cache.refixes$getLifetime().closedFuture();
        }
    }

    private final WeakReference<World> owner;
    private final List<Entry> caches = new ArrayList<>();
    private final CompletableFuture<Void> exited = new CompletableFuture<>();
    private boolean initialized;
    private boolean stopping;
    private boolean watching;
    private Thread thread;

    public IndexedWorldStorage(World world) {
        owner = new WeakReference<>(world);
    }

    public boolean register(IndexedCacheLifetime cache) {
        Entry entry = new Entry(cache);
        synchronized (this) {
            if (stopping) return false;
            initialized = true;
            caches.add(entry);
        }
        entry.closed.whenComplete((ignored, error) -> {
            synchronized (this) {
                caches.remove(entry);
            }
        });
        retain();
        return true;
    }

    public void stopping(Thread thread) {
        synchronized (this) {
            stopping = true;
            if (this.thread == null) this.thread = thread;
        }
        retain();
    }

    public synchronized boolean isStopping() {
        return stopping;
    }

    public boolean retain(CompletableFuture<?> write) {
        World world = owner.get();
        Thread current;
        synchronized (this) {
            if (!initialized || !stopping || world == null) return false;
            current = thread;
        }
        RetiredWorlds.retainOwned(world, current, write);
        return true;
    }

    private void retain() {
        World world = owner.get();
        if (world == null) return;
        Thread current;
        boolean watch;
        List<Entry> pending = new ArrayList<>();
        synchronized (this) {
            if (!stopping || !initialized) return;
            current = thread;
            watch = !watching;
            watching = true;
            for (Entry entry : caches) {
                if (!entry.retained) {
                    entry.retained = true;
                    pending.add(entry);
                }
            }
        }
        if (watch) RetiredWorlds.retainOwned(world, current, exited);
        for (Entry entry : pending) RetiredWorlds.retainOwned(world, current, entry.closed);
        if (!watch) return;
        try {
            RetiredWorlds.exited(current).thenRun(() -> exited.complete(null));
        } catch (RuntimeException | Error failure) {
            Logs.logger().atSevere().withCause(failure).log("Could not track indexed world shutdown");
        }
    }

    public void closeRemaining() {
        List<Entry> remaining;
        synchronized (this) {
            remaining = new ArrayList<>(caches);
        }
        for (Entry entry : remaining) {
            entry.cache.refixes$closeAsync().whenComplete((ignored, error) -> {
                if (error != null)
                    Logs.logger().atWarning().withCause(error).log("Indexed world storage did not close successfully");
            });
        }
    }
}
