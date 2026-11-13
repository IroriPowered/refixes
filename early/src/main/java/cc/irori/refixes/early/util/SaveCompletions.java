package cc.irori.refixes.early.util;

import cc.irori.refixes.early.duck.SaveCompletionOwner;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class SaveCompletions implements Executor {

    private static final HytaleLogger LOGGER = Logs.logger();

    private final World world;
    private final Queue<Task> pending = new ConcurrentLinkedQueue<>();
    private final Set<CompletableFuture<?>> tracked = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Thread> drainer = new AtomicReference<>();
    private volatile Thread waiter;

    public SaveCompletions(World world) {
        this.world = world;
    }

    public static CompletableFuture<?> whenCompleteAsync(
            CompletableFuture<?> future,
            BiConsumer<?, ?> action,
            Executor executor,
            Operation<CompletableFuture<?>> original) {
        if (!(executor instanceof SaveCompletionOwner owner)) return original.call(future, action, executor);
        SaveCompletions owned = owner.refixes$saveCompletions();
        CompletableFuture<?> stage = original.call(future, action, owned);
        owned.track(stage);
        return stage;
    }

    public static CompletableFuture<?> whenCompleteAsync(
            CompletableFuture<?> future,
            BiConsumer<?, ?> action,
            Executor executor,
            Operation<CompletableFuture<?>> original,
            WorldChunk chunk) {
        chunk.addKeepLoaded();
        CompletableFuture<?> stage;
        try {
            stage = whenCompleteAsync(future, action, executor, original);
        } catch (RuntimeException | Error failure) {
            chunk.removeKeepLoaded();
            throw failure;
        }
        stage.whenComplete((ignored, failure) -> chunk.removeKeepLoaded());
        return stage;
    }

    public static CompletableFuture<Void> settle(
            World world, Store<?> store, CompletableFuture<Void> first, Supplier<CompletableFuture<Void>> pass) {
        return ((SaveCompletionOwner) (Object) world).refixes$saveCompletions().settle(store, first, pass);
    }

    @Override
    public void execute(Runnable command) {
        Task task = new Task(command);
        world.execute(task);
        pending.add(task);
        if (task.claimed.get()) pending.remove(task);
        wake();
    }

    private void track(CompletableFuture<?> stage) {
        tracked.add(stage);
        stage.whenComplete((ignored, failure) -> {
            tracked.remove(stage);
            wake();
        });
    }

    private void wake() {
        Thread thread = waiter;
        if (thread != null) LockSupport.unpark(thread);
    }

    private CompletableFuture<Void> settle(
            Store<?> store, CompletableFuture<Void> first, Supplier<CompletableFuture<Void>> pass) {
        Thread current = Thread.currentThread();
        if (!owner(store) || !drainer.compareAndSet(null, current)) return first;
        try {
            first.whenComplete((ignored, failure) -> wake());
            if (!drain(store, first, current)) return first;
            Throwable failure = failure(first);
            CompletableFuture<Void> second;
            try {
                second = pass.get();
            } catch (RuntimeException e) {
                if (failure == null) throw e;
                suppress(failure, e);
                return first;
            }
            if (failure == null) return second;
            return second.handle((ignored, later) -> {
                        if (later != null) suppress(failure, later);
                        return null;
                    })
                    .thenCompose(ignored -> first);
        } finally {
            drainer.compareAndSet(current, null);
        }
    }

    private boolean owner(Store<?> store) {
        return store.isInThread() && world.isInThread();
    }

    private boolean drain(Store<?> store, CompletableFuture<?> first, Thread current) {
        boolean ran = false;
        boolean interrupted = false;
        try {
            while (owner(store)) {
                Task task = pending.poll();
                if (task != null) {
                    try {
                        ran |= task.claimAndRun();
                    } catch (RuntimeException e) {
                        ran = true;
                        LOGGER.atSevere().withCause(e).log("Failed to run save completion for %s", world.getName());
                    }
                    continue;
                }
                if (first.isDone() && tracked.isEmpty()) return ran;
                waiter = current;
                if (pending.isEmpty() && !(first.isDone() && tracked.isEmpty())) LockSupport.park(this);
                waiter = null;
                if (Thread.interrupted()) interrupted = true;
            }
            return false;
        } finally {
            waiter = null;
            if (interrupted) current.interrupt();
        }
    }

    private static Throwable failure(CompletableFuture<?> future) {
        if (!future.isCompletedExceptionally()) return null;
        try {
            future.join();
            return null;
        } catch (CompletionException e) {
            return e.getCause() == null ? e : e.getCause();
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static void suppress(Throwable first, Throwable later) {
        Throwable cause = later instanceof CompletionException && later.getCause() != null ? later.getCause() : later;
        if (cause != first) first.addSuppressed(cause);
    }

    private final class Task implements Runnable {
        private final Runnable command;
        private final AtomicBoolean claimed = new AtomicBoolean();

        private Task(Runnable command) {
            this.command = command;
        }

        @Override
        public void run() {
            claimAndRun();
        }

        private boolean claimAndRun() {
            if (!claimed.compareAndSet(false, true)) return false;
            pending.remove(this);
            command.run();
            return true;
        }
    }
}
