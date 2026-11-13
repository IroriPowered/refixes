package cc.irori.refixes.early.util;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.duck.IndexedWorldOwner;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public final class RetiredWorlds {

    private static final HytaleLogger LOGGER = Logs.logger();
    private static final long REOPEN_WAIT_SECONDS = 30L;
    private static final Object LOCK = new Object();
    private static final Map<Path, Slot> SLOTS = new HashMap<>();
    private static final Map<Path, List<Read>> READS = new HashMap<>();
    private static final ThreadLocal<Read> READING = new ThreadLocal<>();
    private static final Map<Thread, CompletableFuture<Void>> EXITS = new ConcurrentHashMap<>();

    public static final class ReopenConflict extends IllegalStateException {
        final boolean staleRead;
        final Path path;

        public ReopenConflict(String name) {
            this(name, false, null);
        }

        ReopenConflict(String name, boolean staleRead, Path path) {
            super(
                    staleRead
                            ? "World '" + name + "' changed generation while its config was being read"
                            : "World '" + name + "' is still owned by its previous generation");
            this.staleRead = staleRead;
            this.path = path;
        }
    }

    private static final class Read {
        final Path path;
        Object config;
        boolean stale;

        Read(Path path) {
            this.path = path;
        }
    }

    private static final class Slot {
        final List<Object> threads = new ArrayList<>();
        final Map<Thread, Integer> transactions = new HashMap<>();
        final Map<Object, Runnable> afterIdle = new java.util.LinkedHashMap<>();
        final CompletableFuture<Void> idle = new CompletableFuture<>();
        boolean releasing;
    }

    private static final class Owned {
        final Thread thread;

        Owned(Thread thread) {
            this.thread = thread;
        }
    }

    private RetiredWorlds() {}

    public static void created(World world) {
        Path path = key(world.getSavePath());
        synchronized (LOCK) {
            if (SLOTS.containsKey(path)) throw new ReopenConflict(world.getName(), false, path);
        }
    }

    public static Path beginRemoval(World world) {
        Path path = key(world.getSavePath());
        synchronized (LOCK) {
            SLOTS.computeIfAbsent(path, ignored -> new Slot())
                    .transactions
                    .merge(Thread.currentThread(), 1, Integer::sum);
            List<Read> reads = READS.get(path);
            if (reads != null) {
                for (Read read : reads) read.stale = true;
            }
        }
        return path;
    }

    public static void endRemoval(Path path) {
        synchronized (LOCK) {
            Slot slot = SLOTS.get(path);
            if (slot == null) return;
            slot.transactions.computeIfPresent(
                    Thread.currentThread(), (thread, count) -> count == 1 ? null : count - 1);
        }
        release(path, null);
    }

    public static Path reserve(World world, Thread thread) {
        return reserve(world.getSavePath(), thread);
    }

    public static Path reserve(Path savePath, Thread thread) {
        if (thread == null || !thread.isAlive()) return null;
        Path path = key(savePath);
        synchronized (LOCK) {
            SLOTS.computeIfAbsent(path, ignored -> new Slot()).threads.add(thread);
        }
        return path;
    }

    public static void retire(Path path, World world, Thread thread) {
        if (path == null) return;
        if (!thread.isAlive()) {
            finish(path, world, thread);
            return;
        }
        if (!holds(path, thread)) return;
        String name = world.getName();
        LOGGER.atWarning().log(
                "World '%s' was removed while its thread %s is still running. Its save folder stays reserved until it exits",
                name, thread.getName());
        exited(thread).thenRun(() -> {
            LOGGER.atInfo().log("Previous thread of world '%s' finished", name);
            finish(path, world, thread);
        });
    }

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

    private static void finish(Path path, World world, Thread thread) {
        if (!holds(path, thread)) return;
        retainPending(path, world);
        release(path, thread);
    }

    public static void drain(World world) {
        retainPending(key(world.getSavePath()), world);
    }

    private static void retainPending(Path path, World world) {
        CompletableFuture<Void> saving;
        try {
            Store<ChunkStore> store = world.getChunkStore().getStore();
            if (store == null) return;
            saving = store.getResource(ChunkStore.SAVE_RESOURCE).waitForSavingChunks();
        } catch (Throwable t) {
            LOGGER.atSevere().withCause(t).log(
                    "Could not track pending chunk saves of world '%s'. Its save folder stays reserved",
                    world.getName());
            saving = new CompletableFuture<>();
        }
        hold(path, saving);
    }

    public static void quiesce(World world) {
        release(key(world.getSavePath()), Thread.currentThread());
    }

    public static void retain(Path savePath, CompletableFuture<?> write) {
        if (!EarlyOptions.isMixinEnabled("MixinWorldRetirement")) return;
        hold(key(savePath), write);
    }

    public static void retain(World world, CompletableFuture<?> write) {
        if (world instanceof IndexedWorldOwner owner
                && owner.refixes$indexedStorage().retain(write)) return;
        retain(world.getSavePath(), write);
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

    private static void hold(Path path, CompletableFuture<?> write) {
        if (write.isDone()) return;
        synchronized (LOCK) {
            SLOTS.computeIfAbsent(path, ignored -> new Slot()).threads.add(write);
        }
        write.whenComplete((ignored, error) -> release(path, write));
    }

    private static boolean holds(Path path, Object holder) {
        synchronized (LOCK) {
            Slot slot = SLOTS.get(path);
            return slot != null && slot.threads.contains(holder);
        }
    }

    private static void release(Path path, Object holder) {
        Slot released;
        synchronized (LOCK) {
            Slot slot = SLOTS.get(path);
            if (slot == null) return;
            if (holder != null && !slot.threads.remove(holder)) return;
            if (!slot.threads.isEmpty() || !slot.transactions.isEmpty() || slot.releasing) return;
            slot.releasing = true;
        }
        while (true) {
            Runnable action;
            synchronized (LOCK) {
                Slot slot = SLOTS.get(path);
                if (slot == null) return;
                if (!slot.threads.isEmpty() || !slot.transactions.isEmpty()) {
                    slot.releasing = false;
                    return;
                }
                if (slot.afterIdle.isEmpty()) {
                    SLOTS.remove(path);
                    released = slot;
                    break;
                }
                var actions = slot.afterIdle.values().iterator();
                action = actions.next();
                actions.remove();
            }
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

    public static <K, V> V claim(
            String name, Path savePath, Map<K, V> worlds, K key, V world, Object config, Supplier<V> insert) {
        Path path = key(savePath);
        synchronized (LOCK) {
            V previous = insert.get();
            if (previous == null) {
                boolean stale = false;
                for (List<Read> reads : READS.values()) {
                    for (Read read : reads) {
                        if (read.config == config && (read.stale || !read.path.equals(path))) stale = true;
                    }
                }
                if (stale || SLOTS.containsKey(path)) {
                    worlds.computeIfPresent(key, (name0, current) -> current == world ? null : current);
                    throw new ReopenConflict(name, stale, path);
                }
            }
            return previous;
        }
    }

    public static <T> CompletableFuture<T> read(Path savePath, Supplier<CompletableFuture<T>> load) {
        Read read = new Read(key(savePath));
        synchronized (LOCK) {
            read.stale = SLOTS.containsKey(read.path);
            READS.computeIfAbsent(read.path, ignored -> new ArrayList<>()).add(read);
        }
        Read previous = READING.get();
        READING.set(read);
        CompletableFuture<T> loading;
        try {
            loading = load.get();
        } catch (RuntimeException | Error failure) {
            endRead(read);
            throw failure;
        } finally {
            if (previous == null) READING.remove();
            else READING.set(previous);
        }
        return loading.whenComplete((ignored, error) -> endRead(read));
    }

    public static <T> CompletableFuture<T> bindRead(CompletableFuture<T> config) {
        Read read = READING.get();
        if (read == null) return config;
        return config.thenApply(value -> {
            synchronized (LOCK) {
                read.config = value;
            }
            return value;
        });
    }

    private static void endRead(Read read) {
        synchronized (LOCK) {
            List<Read> reads = READS.get(read.path);
            if (reads != null && reads.remove(read) && reads.isEmpty()) READS.remove(read.path);
        }
    }

    public static <T> CompletableFuture<T> afterRelease(
            String name, Path savePath, Supplier<CompletableFuture<T>> open) {
        return afterRelease(name, savePath, false, open);
    }

    public static <T> CompletableFuture<T> afterRelease(
            String name, Path savePath, boolean reread, Supplier<CompletableFuture<T>> open) {
        return afterRelease(
                name, key(savePath), open, reread, System.nanoTime() + TimeUnit.SECONDS.toNanos(REOPEN_WAIT_SECONDS));
    }

    private static <T> CompletableFuture<T> afterRelease(
            String name, Path path, Supplier<CompletableFuture<T>> open, boolean reread, long deadline) {
        CompletableFuture<Void> idle;
        synchronized (LOCK) {
            Slot slot = SLOTS.get(path);
            if (slot == null) {
                idle = null;
            } else if (slot.threads.contains(Thread.currentThread())
                    || slot.threads.stream()
                            .anyMatch(holder -> holder instanceof Owned owner && owner.thread == Thread.currentThread())
                    || slot.transactions.containsKey(Thread.currentThread())) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "World '" + name + "' cannot be reopened from its own stopping thread"));
            } else {
                idle = slot.idle;
            }
        }
        if (idle == null) {
            return open.get().exceptionallyCompose(error -> {
                Throwable cause =
                        error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                if (cause instanceof ReopenConflict conflict
                        && (reread || !conflict.staleRead)
                        && System.nanoTime() < deadline) {
                    return afterRelease(name, conflict.path == null ? path : conflict.path, open, reread, deadline);
                }
                return CompletableFuture.failedFuture(cause);
            });
        }
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
                .thenComposeAsync(ignored -> afterRelease(name, path, open, reread, deadline));
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
