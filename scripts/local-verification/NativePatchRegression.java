package local.hyinit.verify;

import cc.irori.refixes.early.util.ParallelFailure;
import cc.irori.refixes.early.util.SectionLockHold;
import com.hypixel.hytale.component.task.ParallelRangeTask;
import com.hypixel.hytale.component.task.ParallelTask;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.StampedLock;
import java.util.function.IntConsumer;

public final class NativePatchRegression {
    private NativePatchRegression() {}

    public static void assertParallelTask() throws Exception {
        if (!ParallelFailure.Managed.class.isAssignableFrom(ParallelTask.class)) {
            throw new AssertionError("ParallelTask was not transformed");
        }
        empty(false);
        empty(true);
        failureDraining();
        System.out.println("[LocalVerify] ParallelTask regressions PASS");
    }

    public static void assertLoadState() throws Exception {
        loadStateRetry();
        loadStateReentrancy();
        System.out.println("[LocalVerify] Chunk load state regressions PASS");
    }

    private static void empty(boolean range) {
        try {
            if (range) {
                new ParallelRangeTask<IntConsumer>(() -> ignored -> {}).init(0, 0).invoke();
            } else {
                ParallelTask<IntConsumer> task = new ParallelTask<>(() -> ignored -> {});
                task.init();
                task.doInvoke();
            }
        } catch (ArrayIndexOutOfBoundsException failure) {
            throw new AssertionError("Empty task must complete normally", failure);
        }
    }

    private static void failureDraining() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch throwing = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicInteger cleared = new AtomicInteger();
        AtomicInteger work = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ParallelTask<Data> task = new ParallelTask<>(() -> new Data(cleared));
        task.init();
        ParallelRangeTask<Data> slow = task.appendTask().init(0, 1);
        slow.get(0).action = ignored -> {
            entered.countDown();
            await(release);
            work.incrementAndGet();
        };
        ParallelRangeTask<Data> fast = task.appendTask().init(0, 1);
        fast.get(0).action = ignored -> {
            await(entered);
            throwing.countDown();
            throw new IllegalStateException("expected-worker-failure");
        };
        Thread caller = new Thread(() -> {
            try {
                task.doInvoke();
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                finished.countDown();
            }
        }, "native-patch-parallel-caller");
        caller.setDaemon(true);
        caller.start();
        try {
            if (!throwing.await(5, TimeUnit.SECONDS)) throw new AssertionError("Failure worker did not run");
            if (finished.await(250, TimeUnit.MILLISECONDS)) {
                throw new AssertionError("Caller returned before the sibling worker drained");
            }
        } finally {
            release.countDown();
        }
        if (!finished.await(5, TimeUnit.SECONDS)) throw new AssertionError("Parallel caller did not finish");
        if (!(failure.get() instanceof IllegalStateException)) {
            throw new AssertionError("Worker failure was lost", failure.get());
        }
        if (work.get() != 1 || cleared.get() != 2) {
            throw new AssertionError("Failed batch must drain both workers and discard both data objects");
        }
        task.init();
        task.appendTask().init(0, 1).get(0).action = ignored -> work.incrementAndGet();
        task.doInvoke();
        if (work.get() != 2) throw new AssertionError("Drained task was not reusable");
    }

    private static void loadStateRetry() throws Exception {
        Object state = newLoadState();
        set(state, "flags", 7);
        boolean cleanup = hasField(state, "cleanupAttached");
        if (cleanup) set(state, "cleanupAttached", true);
        set(state, "future", CompletableFuture.failedFuture(new IllegalStateException("stale")));
        set(state, "throwable", new IllegalStateException("stale"));
        set(state, "failedCounter", 1);
        set(state, "failedWhen", System.nanoTime() - TimeUnit.HOURS.toNanos(1));
        Method reset = state.getClass().getDeclaredMethod("checkAndResetBackoff");
        reset.setAccessible(true);
        if (Boolean.TRUE.equals(reset.invoke(state))) throw new AssertionError("Fixture backoff did not expire");
        boolean fresh = get(state, "future") == null && ((Integer) get(state, "flags")) == 0
                && !(cleanup && (Boolean) get(state, "cleanupAttached"));
        if (!fresh) throw new AssertionError("Failed load chain was not reset after backoff");
    }

    private static void loadStateReentrancy() throws Exception {
        Object state = newLoadState();
        StampedLock lock = (StampedLock) get(state, "lock");
        Method fail = state.getClass().getDeclaredMethod("fail", Throwable.class);
        fail.setAccessible(true);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            long stamp = lock.writeLock();
            SectionLockHold.acquire(lock);
            held.countDown();
            try {
                fail.invoke(state, new IllegalStateException("expected-load-failure"));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                lock.unlockWrite(stamp);
                SectionLockHold.release(lock);
                done.countDown();
            }
        }, "native-patch-load-state-lock");
        caller.setDaemon(true);
        caller.start();
        if (!held.await(5, TimeUnit.SECONDS)) throw new AssertionError("Load-state caller did not start");
        if (!done.await(500, TimeUnit.MILLISECONDS) || failure.get() != null) {
            throw new AssertionError("Reentrant load failure did not complete", failure.get());
        }
    }

    private static Object newLoadState() throws Exception {
        Class<?> type = Class.forName("com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid$LoadState");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean hasField(Object target, String name) {
        try {
            target.getClass().getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException absent) {
            return false;
        }
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Worker latch timeout");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }

    private static final class Data implements IntConsumer, ParallelFailure.Discardable {
        private final AtomicInteger cleared;
        private IntConsumer action;

        private Data(AtomicInteger cleared) {
            this.cleared = cleared;
        }

        @Override
        public void accept(int value) {
            action.accept(value);
        }

        @Override
        public void clear() {
            action = null;
            cleared.incrementAndGet();
        }
    }
}
