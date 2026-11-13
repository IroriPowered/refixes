package cc.irori.refixes.early.util;

import cc.irori.refixes.early.duck.IndexedWorldOwner;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class RetiredWorlds {

    private static final HytaleLogger LOGGER = Logs.logger();
    private static final long REOPEN_WAIT_SECONDS = 30L;
    private static final Object LOCK = new Object();
    private static final Map<Path, Slot> SLOTS = new HashMap<>();
    private static final Map<Thread, CompletableFuture<Void>> EXITS = new ConcurrentHashMap<>();

    private static final class Slot {
        final List<Object> threads = new ArrayList<>();
        final Map<Object, Runnable> afterIdle = new LinkedHashMap<>();
        final CompletableFuture<Void> idle = new CompletableFuture<>();
    }

    private static final class Owned {
        final Thread thread;

        Owned(Thread thread) {
            this.thread = thread;
        }
    }

    private RetiredWorlds() {}

    public static CompletableFuture<Void> exited(Thread thread) {
        if (thread == null || !thread.isAlive()) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> created = new CompletableFuture<>();
        CompletableFuture<Void> existing = EXITS.putIfAbsent(thread, created);
        if (existing != null) return existing;
        try {
            Thread.ofVirtual().name("Refixes world exit " + thread.getName()).start(() -> {
                while (thread.isAlive()) {
                    try {
                        thread.join();
                    } catch (InterruptedException ignored) {
                    }
                }
                EXITS.remove(thread, created);
                created.complete(null);
            });
        } catch (RuntimeException | Error failure) {
            EXITS.remove(thread, created);
            throw failure;
        }
        return created;
    }

    public static void retain(World world, CompletableFuture<?> write) {
        if (world instanceof IndexedWorldOwner owner)
            owner.refixes$indexedStorage().retain(write);
    }

    public static void retainOwned(World world, Thread thread, CompletableFuture<?> write) {
        if (write.isDone()) return;
        Path path = key(world.getSavePath());
        Owned owner = new Owned(thread);
        synchronized (LOCK) {
            SLOTS.computeIfAbsent(path, ignored -> new Slot()).threads.add(owner);
        }
        write.whenComplete((ignored, error) -> release(path, owner));
    }

    private static void release(Path path, Object holder) {
        Slot released;
        synchronized (LOCK) {
            Slot slot = SLOTS.get(path);
            if (slot == null || !slot.threads.remove(holder) || !slot.threads.isEmpty()) return;
            SLOTS.remove(path);
            released = slot;
        }
        for (Runnable action : released.afterIdle.values()) {
            try {
                action.run();
            } catch (Throwable t) {
                LOGGER.atSevere().withCause(t).log("Deferred world cleanup failed for %s", path);
            }
        }
        released.idle.complete(null);
    }

    public static boolean runWhenIdle(Path savePath, Object owner, Runnable action) {
        synchronized (LOCK) {
            Slot slot = SLOTS.get(key(savePath));
            if (slot == null) return false;
            slot.afterIdle.putIfAbsent(owner, action);
            return true;
        }
    }

    public static <T> CompletableFuture<T> afterRelease(
            String name, Path savePath, Supplier<CompletableFuture<T>> open) {
        return afterRelease(
                name, key(savePath), open, System.nanoTime() + TimeUnit.SECONDS.toNanos(REOPEN_WAIT_SECONDS));
    }

    private static <T> CompletableFuture<T> afterRelease(
            String name, Path path, Supplier<CompletableFuture<T>> open, long deadline) {
        CompletableFuture<Void> idle;
        synchronized (LOCK) {
            Slot slot = SLOTS.get(path);
            if (slot == null) {
                idle = null;
            } else if (slot.threads.stream()
                    .anyMatch(holder -> holder instanceof Owned owner && owner.thread == Thread.currentThread())) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "World '" + name + "' cannot be reopened from its own stopping thread"));
            } else {
                idle = slot.idle;
            }
        }
        if (idle == null) return open.get();
        LOGGER.atWarning().log("Waiting for the previous generation of world '%s' to finish before opening it", name);
        long remaining = Math.max(1L, deadline - System.nanoTime());
        return idle.copy()
                .orTimeout(remaining, TimeUnit.NANOSECONDS)
                .handle((ignored, error) -> {
                    if (error instanceof TimeoutException) {
                        throw new IllegalStateException("World '" + name
                                + "' is still being written by its previous generation. Try again after it finishes");
                    }
                    return null;
                })
                .thenComposeAsync(ignored -> afterRelease(name, path, open, deadline));
    }

    static Path key(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return absolute.toRealPath();
        } catch (IOException ignored) {
        }
        Path parent = absolute.getParent();
        if (parent == null) return absolute;
        try {
            return parent.toRealPath().resolve(absolute.getFileName());
        } catch (IOException e) {
            return absolute;
        }
    }
}
