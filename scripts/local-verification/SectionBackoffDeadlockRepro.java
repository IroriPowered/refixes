package local.hyinit.verify;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.IResourceStorage;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

public final class SectionBackoffDeadlockRepro {
    public static final int SECTION_X = 0;
    public static final int SECTION_Y = 1;
    public static final int SECTION_Z = 0;
    public static final long JOIN_MS = 2500L;

    private SectionBackoffDeadlockRepro() {}

    public static void assertPatched(World world) throws Exception {
        if (world == null) {
            throw new AssertionError("A World is required for the deadlock fixture");
        }
        int flags = world.getWorldConfig() == null ? GetChunkFlags.NO_GENERATE : 0;
        ChunkStore chunkStore = new ChunkStore(world);
        Store<ChunkStore> store = newStore(chunkStore);
        setDeclared(ChunkGrid.class, chunkStore, "store", store);
        installEmptyStorage(chunkStore);
        if (chunkStore.getStore() == null) {
            throw new AssertionError("ChunkStore.store was not installed");
        }
        if (store.isShutdown()) {
            throw new AssertionError("fixture Store reported shutdown");
        }
        seedColumnBackoff(chunkStore, SECTION_X, SECTION_Z);

        AtomicReference<CompletableFuture<?>> returned = new AtomicReference<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                returned.set(chunkStore.getChunkSectionReferenceAsync(SECTION_X, SECTION_Y, SECTION_Z, flags));
            } catch (Throwable error) {
                thrown.set(error);
            }
        }, "local-verify-section-backoff");
        worker.setDaemon(true);
        worker.start();
        worker.join(JOIN_MS);
        if (worker.isAlive()) {
            StringBuilder stack = new StringBuilder();
            for (StackTraceElement frame : worker.getStackTrace()) {
                stack.append('\n').append(frame);
            }
            throw new AssertionError("ChunkStore section-backoff still deadlocks:" + stack);
        }
        if (thrown.get() != null) {
            throw new AssertionError("Section request threw instead of completing a failed future", thrown.get());
        }
        CompletableFuture<?> future = returned.get();
        boolean pump = world.getWorldConfig() == null;
        long deadline = System.nanoTime() + JOIN_MS * 1_000_000L;
        while (future != null && !future.isDone() && System.nanoTime() < deadline) {
            if (pump) {
                drainWorld(world);
            }
            Thread.sleep(10L);
        }
        if (future == null) {
            throw new AssertionError("Section request returned no future");
        }
        if (!future.isDone()) {
            throw new AssertionError("Section future never settled within " + JOIN_MS
                    + "ms of draining the world executor");
        }
        if (!future.isCompletedExceptionally()) {
            throw new AssertionError("Failed column must not yield a successful section future");
        }
        if (pump) {
            assertRetryAfterBackoff(chunkStore, world, flags, pump, future);
        }
        assertLockHandoff();
        System.out.println("[LocalVerify] ChunkStore section-backoff deadlock regression PASS");
    }

    static void assertRetryAfterBackoff(
            ChunkStore chunkStore, World world, int flags, boolean pump, CompletableFuture<?> failed)
            throws Exception {
        Object chunks = fieldValue(ChunkGrid.class, chunkStore, "chunks");
        chunks.getClass()
                .getMethod("remove", long.class)
                .invoke(chunks, Long.valueOf(ChunkUtil.indexChunk(SECTION_X, SECTION_Z)));
        expireBackoff(sectionState(chunkStore));

        CompletableFuture<?> retry =
                chunkStore.getChunkSectionReferenceAsync(SECTION_X, SECTION_Y, SECTION_Z, flags);
        if (retry == failed) {
            throw new AssertionError("Expired backoff reused the stale failed section future");
        }
        long deadline = System.nanoTime() + JOIN_MS * 1_000_000L;
        while (!retry.isDone() && System.nanoTime() < deadline) {
            if (pump) {
                drainWorld(world);
            }
            Thread.sleep(10L);
        }
        if (!retry.isDone()) {
            throw new AssertionError("Retry after backoff never settled");
        }
        if (retry.isCompletedExceptionally()) {
            throw new AssertionError("Retry after backoff replayed the original failure");
        }
        System.out.println("[LocalVerify] ChunkStore retry-after-backoff regression PASS");
    }

    static Object sectionState(ChunkStore chunkStore) throws Exception {
        Object sections = fieldValue(ChunkGrid.class, chunkStore, "chunkSections");
        for (Method method : sections.getClass().getMethods()) {
            if (method.getName().equals("get") && method.getParameterCount() == 3) {
                method.setAccessible(true);
                Object state = method.invoke(sections, SECTION_X, SECTION_Y, SECTION_Z);
                if (state != null) {
                    return state;
                }
            }
        }
        throw new AssertionError("Section load state was not retained for the retry check");
    }

    static void expireBackoff(Object state) throws Exception {
        Class<?> loadState = Class.forName(
                "com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid$LoadState");
        setDeclared(loadState, state, "failedCounter", Integer.valueOf(1));
        setDeclared(loadState, state, "failedWhen", Long.valueOf(System.nanoTime() - 3_600_000_000_000L));
    }

    static void installEmptyStorage(ChunkStore chunkStore) throws Exception {
        Class<?> provider = Class.forName(
                "com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider");
        Object instance = provider.getField("INSTANCE").get(null);
        Object loader = provider.getMethod("getLoader", Object.class, Store.class)
                .invoke(instance, null, chunkStore.getStore());
        Object saver = provider.getMethod("getSaver", Object.class, Store.class)
                .invoke(instance, null, chunkStore.getStore());
        setDeclared(ChunkGrid.class, chunkStore, "loader", loader);
        setDeclared(ChunkStore.class, chunkStore, "saver", saver);
    }

    static void drainWorld(World world) throws Exception {
        Object queue = fieldValue(World.class, world, "taskQueue");
        if (!(queue instanceof java.util.Deque<?> tasks)) {
            return;
        }
        Object task;
        while ((task = tasks.poll()) != null) {
            ((Runnable) task).run();
        }
    }

    static World setupPhaseWorld() throws Exception {
        World world = allocate(World.class);
        setDeclared(TickingThread.class, world, "currentRun", new AtomicReference<>());
        Method setThread = TickingThread.class.getDeclaredMethod("setThread", Thread.class);
        setThread.setAccessible(true);
        setThread.invoke(world, Thread.currentThread());
        setDeclared(World.class, world, "name", "local-verify-setup");
        setDeclared(World.class, world, "acceptingTasks", new java.util.concurrent.atomic.AtomicBoolean(true));
        setDeclared(World.class, world, "taskQueue", new java.util.concurrent.LinkedBlockingDeque<Runnable>());
        return world;
    }

    public static Store<ChunkStore> fixtureStore() throws Exception {
        World world = setupPhaseWorld();
        setDeclared(World.class, world, "worldConfig", new WorldConfig());
        ChunkStore chunkStore = new ChunkStore(world);
        Store<ChunkStore> store = newStore(chunkStore);
        setDeclared(ChunkGrid.class, chunkStore, "store", store);
        return store;
    }
    static void assertLockHandoff() throws Exception {
        Class<?> hold = Class.forName("cc.irori.refixes.early.util.SectionLockHold");
        Method acquire = hold.getMethod("acquire", StampedLock.class);
        Method release = hold.getMethod("release", StampedLock.class);
        Method held = hold.getMethod("heldByCurrentThread", StampedLock.class);
        StampedLock lock = new StampedLock();
        acquire.invoke(null, lock);
        long stamp = lock.writeLock();
        AtomicLong other = new AtomicLong();
        Thread rival = new Thread(() -> other.set(lock.writeLock()), "lock-handoff");
        rival.start();
        lock.unlockWrite(stamp);
        rival.join(JOIN_MS);
        if (rival.isAlive()) {
            throw new AssertionError("Handoff thread did not acquire the write lock");
        }
        release.invoke(null, lock);
        if (Boolean.TRUE.equals(held.invoke(null, lock))) {
            throw new AssertionError("SectionLockHold still tracks after release while another thread owns the lock");
        }
        if (other.get() != 0L) {
            lock.unlockWrite(other.get());
        }
    }


    public static void seedColumnBackoff(ChunkStore chunkStore, int chunkX, int chunkZ) throws Exception {
        Object chunks = fieldValue(ChunkGrid.class, chunkStore, "chunks");
        Class<?> columnStateClass = Class.forName(
                "com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid$ChunkLoadState");
        Constructor<?> ctor = columnStateClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object columnState = ctor.newInstance();

        Class<?> loadStateClass = Class.forName(
                "com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid$LoadState");
        setDeclared(loadStateClass, columnState, "throwable", new RuntimeException("repro-column-failure"));
        setDeclared(loadStateClass, columnState, "failedWhen", System.nanoTime());
        setDeclared(loadStateClass, columnState, "failedCounter", Integer.valueOf(10_000));

        Method put = chunks.getClass().getMethod("put", long.class, Object.class);
        put.invoke(chunks, Long.valueOf(ChunkUtil.indexChunk(chunkX, chunkZ)), columnState);
    }

    @SuppressWarnings("unchecked")
    public static Store<ChunkStore> newStore(ChunkStore chunkStore) throws Exception {
        Exception last = null;
        for (Constructor<?> ctor : Store.class.getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length != 4) {
                continue;
            }
            if (!ComponentRegistry.class.isAssignableFrom(params[0])) {
                continue;
            }
            if (params[1] != int.class) {
                continue;
            }
            if (!IResourceStorage.class.isAssignableFrom(params[3])) {
                continue;
            }
            ctor.setAccessible(true);
            try {
                return (Store<ChunkStore>) ctor.newInstance(
                        ChunkStore.REGISTRY, Integer.valueOf(0), chunkStore, EmptyResourceStorage.get());
            } catch (Exception e) {
                last = e;
            }
        }
        try {
            return allocate(Store.class);
        } catch (Exception e) {
            if (last != null) {
                e.addSuppressed(last);
            }
            throw e;
        }
    }

    static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        Object unsafe = theUnsafe.get(null);
        Method allocate = unsafeClass.getMethod("allocateInstance", Class.class);
        return type.cast(allocate.invoke(unsafe, type));
    }

    static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    static Object fieldValue(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    static Object fieldValue(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    static void setDeclared(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
