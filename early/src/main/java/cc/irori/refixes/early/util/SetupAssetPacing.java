package cc.irori.refixes.early.util;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.OutgoingQueueLimits;
import cc.irori.refixes.early.duck.QuicheDrainSignal;
import cc.irori.refixes.early.duck.QuicheQueueOwner;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.lib.quiche.QuicheChannel;
import com.hypixel.hytale.protocol.io.ChannelConnection;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.common.events.SendCommonAssetsEvent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

public final class SetupAssetPacing {

    private static final long WALL_CLOCK_TIMEOUT_MULTIPLIER = 10L;
    private static final long PROGRESS_CHECK_NANOS = TimeUnit.MILLISECONDS.toNanos(100L);
    private static final ThreadLocal<PacketHandler> OWNER = new ThreadLocal<>();
    private static final CancellationException CLOSED = new CancellationException();
    private static final Map<PacketHandler, Clock> CLOCKS = new WeakHashMap<>();
    private static final ThreadLocal<Set<Object>> BROADCAST = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> MONITOR = new ThreadLocal<>();

    private SetupAssetPacing() {}

    public static Function<CompletableFuture<SendCommonAssetsEvent>, CompletableFuture<SendCommonAssetsEvent>> chain(
            Consumer<SendCommonAssetsEvent> consumer) {
        return future -> future.thenCompose(event -> CompletableFuture.supplyAsync(
                () -> {
                    try {
                        consumer.accept(event);
                    } catch (RuntimeException failure) {
                        if (failure != CLOSED) throw failed(event, failure);
                    }
                    return event;
                },
                worker(event.getPacketHandler())));
    }

    private static final Map<PacketHandler, RuntimeException> FAILED = new WeakHashMap<>();

    private static RuntimeException failed(SendCommonAssetsEvent event, RuntimeException failure) {
        synchronized (FAILED) {
            FAILED.put(event.getPacketHandler(), failure);
        }
        return failure;
    }

    public static <T> CompletableFuture<T> failedSetup(PacketHandler handler, CompletableFuture<T> future) {
        return future.thenCompose(value -> {
            RuntimeException failure;
            synchronized (FAILED) {
                failure = FAILED.remove(handler);
            }
            return failure == null ? CompletableFuture.completedFuture(value) : CompletableFuture.failedFuture(failure);
        });
    }

    public static <T> CompletableFuture<Void> continueOnWorker(
            PacketHandler owner, CompletableFuture<T> future, Consumer<? super T> action) {
        return future.thenAcceptAsync(action, worker(owner));
    }

    private static Executor worker(PacketHandler owner) {
        return task -> Thread.ofVirtual().name("Refixes-SetupAssets").start(() -> {
            OWNER.set(owner);
            try {
                task.run();
            } finally {
                OWNER.remove();
            }
        });
    }

    public static void awaitBlob(PacketHandler handler, CompletableFuture<?> blob) {
        if (OWNER.get() != handler) return;
        while (!blob.isDone()) {
            if (!handler.getChannel().isActive()) throw CLOSED;
            try {
                blob.get(1L, TimeUnit.SECONDS);
            } catch (TimeoutException | ExecutionException | CancellationException ignored) {
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Setup asset worker interrupted");
            }
        }
    }

    public static boolean nettyWorker(NettyQueueBudget budget) {
        PacketHandler owner = OWNER.get();
        return owner != null
                && Thread.currentThread().isVirtual()
                && owner.getChannel() instanceof NettyUtil.NettyChannelConnection connection
                && budget.owns(connection.channel());
    }

    public static boolean quicheWorker(QuicheQueueBudget budget) {
        PacketHandler owner = OWNER.get();
        return owner != null
                && Thread.currentThread().isVirtual()
                && owner.getChannel() instanceof QuicheQueueOwner connection
                && connection.refixes$queueBudget() == budget;
    }

    public static void awaitQuiche(QuicheChannel channel, QuicheQueueBudget budget, long bytes, int packets) {
        requireQuicheOpen(channel, budget);
        Clock clock = waiting(OWNER.get());
        try {
            channel.flush();
            CompletableFuture<Void> drained = budget.drained(bytes, packets);
            if (drained.isDone()) drained = ((QuicheDrainSignal) (Object) channel).refixes$drained();
            requireQuicheOpen(channel, budget);
            try {
                drained.get(1L, TimeUnit.SECONDS);
            } catch (TimeoutException | ExecutionException ignored) {
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Setup asset worker interrupted");
            }
            requireQuicheOpen(channel, budget);
        } finally {
            waited(clock);
        }
    }

    private static void requireQuicheOpen(QuicheChannel channel, QuicheQueueBudget budget) {
        PacketHandler owner = OWNER.get();
        if (!channel.isActive() || owner != null && !owner.getChannel().isActive() || budget.overflowed()) throw CLOSED;
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Setup asset worker interrupted");
    }

    public static void broadcast(Runnable broadcast) {
        if (BROADCAST.get() != null || EarlyOptions.outgoingQueueLimits().broadcastWaitMs() == 0L) {
            broadcast.run();
            return;
        }
        BROADCAST.set(Collections.newSetFromMap(new IdentityHashMap<>()));
        try {
            broadcast.run();
        } finally {
            BROADCAST.remove();
        }
    }

    public static void monitorBroadcast(Runnable broadcast) {
        if (MONITOR.get() != null) {
            broadcast.run();
            return;
        }
        MONITOR.set(Boolean.TRUE);
        try {
            broadcast.run();
        } finally {
            MONITOR.remove();
        }
    }

    public static boolean broadcastWaits(Object budget) {
        Set<Object> expired = BROADCAST.get();
        if (expired == null || expired.contains(budget) || OWNER.get() != null) return false;
        boolean waitable = MONITOR.get() != null
                || Thread.currentThread() instanceof ForkJoinWorkerThread worker
                        && worker.getPool() == ForkJoinPool.commonPool();
        return waitable && AssetRegistry.ASSET_LOCK instanceof ReentrantReadWriteLock lock && !lock.isWriteLocked();
    }

    public static boolean broadcastExempt() {
        return BROADCAST.get() != null
                && OWNER.get() == null
                && AssetRegistry.ASSET_LOCK instanceof ReentrantReadWriteLock lock
                && lock.isWriteLocked();
    }

    public static boolean awaitQuicheBroadcast(QuicheChannel channel, QuicheQueueBudget budget) {
        QuicheDrainSignal signal = (QuicheDrainSignal) (Object) channel;
        BroadcastWait wait = new BroadcastWait(budget::progress);
        while (signal.refixes$backlogged()) {
            if (budget.overflowed()) return false;
            channel.flush();
            if (!awaitBroadcast(budget, signal.refixes$drained(), wait)) return false;
        }
        return !budget.overflowed();
    }

    public static boolean awaitNettyBroadcast(NettyQueueBudget budget) {
        BroadcastWait wait = new BroadcastWait(budget::progress);
        while (budget.backlogged()) {
            budget.flush();
            if (!awaitBroadcast(budget, budget.drained(), wait)) return false;
        }
        return budget.failure() == null;
    }

    private static boolean awaitBroadcast(Object budget, CompletableFuture<Void> drained, BroadcastWait wait) {
        while (true) {
            long remaining = wait.remaining();
            if (remaining <= 0L) break;
            try {
                drained.get(Math.min(remaining, PROGRESS_CHECK_NANOS), TimeUnit.NANOSECONDS);
                return true;
            } catch (ExecutionException failed) {
                return true;
            } catch (TimeoutException ignored) {
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        BROADCAST.get().add(budget);
        return false;
    }

    private static final class BroadcastWait {

        private final LongSupplier released;
        private final long started = System.nanoTime();
        private final long waitNanos;
        private final long stallNanos;
        private long seen;
        private long progressed = started;

        private BroadcastWait(LongSupplier released) {
            OutgoingQueueLimits limits = EarlyOptions.outgoingQueueLimits();
            this.released = released;
            waitNanos = TimeUnit.MILLISECONDS.toNanos(limits.broadcastWaitMs());
            stallNanos = TimeUnit.MILLISECONDS.toNanos(limits.broadcastStallMs());
            seen = released.getAsLong();
        }

        private long remaining() {
            long now = System.nanoTime();
            long current = released.getAsLong();
            if (current != seen) {
                seen = current;
                progressed = now;
            }
            long remaining = waitNanos - (now - started);
            return stallNanos == 0L ? remaining : Math.min(remaining, stallNanos - (now - progressed));
        }
    }

    public static boolean reserveNetty(ChannelConnection channel, NettyQueueBudget budget, long bytes, int packets) {
        if (!nettyWorker(budget)) return budget.reserve(bytes, packets);
        requireNettyOpen(channel, budget);
        if (bytes < 0L
                || bytes == Long.MAX_VALUE
                || bytes > budget.maxBytes()
                || packets < 0
                || packets > budget.maxPackets()) return budget.reserve(bytes, packets);
        if (budget.tryReserve(bytes, packets)) return true;
        Clock clock = waiting(OWNER.get());
        try {
            while (true) {
                requireNettyOpen(channel, budget);
                budget.flush();
                CompletableFuture<Void> drained = budget.drained(bytes, packets);
                requireNettyOpen(channel, budget);
                if (budget.tryReserve(bytes, packets)) return true;
                try {
                    drained.get(1L, TimeUnit.SECONDS);
                } catch (TimeoutException | ExecutionException ignored) {
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Setup asset worker interrupted");
                }
            }
        } finally {
            waited(clock);
        }
    }

    private static void requireNettyOpen(ChannelConnection channel, NettyQueueBudget budget) {
        PacketHandler owner = OWNER.get();
        if (!channel.isActive() || owner != null && !owner.getChannel().isActive()) throw CLOSED;
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Setup asset worker interrupted");
        Throwable failure = budget.failure();
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        if (failure != null) throw CLOSED;
    }

    private static Clock waiting(PacketHandler handler) {
        Clock clock = clock(handler);
        synchronized (clock) {
            clock.waitStart = System.nanoTime();
        }
        return clock;
    }

    private static void waited(Clock clock) {
        synchronized (clock) {
            clock.waited += System.nanoTime() - clock.waitStart;
            clock.waitStart = 0L;
        }
    }

    public static void awaitDrain(PacketHandler handler) {
        if (OWNER.get() != handler) return;
        ChannelConnection channel = handler.getChannel();
        if (!channel.isActive()) throw CLOSED;
        if (!(channel instanceof QuicheDrainSignal signal) || !signal.refixes$backlogged()) return;
        Clock clock = waiting(handler);
        try {
            while (channel.isActive() && signal.refixes$backlogged()) {
                try {
                    signal.refixes$drained().get(1L, TimeUnit.SECONDS);
                } catch (TimeoutException | ExecutionException ignored) {
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Setup asset worker interrupted");
                }
            }
            if (!channel.isActive()) throw CLOSED;
        } finally {
            waited(clock);
        }
    }

    public static BooleanSupplier serverTime(
            PacketHandler handler,
            CompletableFuture<?> future,
            String stage,
            Duration timeout,
            BooleanSupplier condition) {
        Clock clock = clock(handler);
        synchronized (clock) {
            clock.started = System.nanoTime();
            clock.waited = 0L;
            if (clock.waitStart != 0L) clock.waitStart = clock.started;
        }
        long limit = timeout.toNanos();
        long wallLimit = limit > Long.MAX_VALUE / WALL_CLOCK_TIMEOUT_MULTIPLIER
                ? Long.MAX_VALUE
                : limit * WALL_CLOCK_TIMEOUT_MULTIPLIER;
        return new ServerTimeCondition(handler, clock, future, stage, limit, wallLimit, condition);
    }

    private static Clock clock(PacketHandler handler) {
        synchronized (CLOCKS) {
            return CLOCKS.computeIfAbsent(handler, ignored -> new Clock());
        }
    }

    private static final class Clock {
        private volatile long started;
        private volatile long waited;
        private volatile long waitStart;

        private synchronized long used(long now) {
            long current = waitStart;
            return now - started - waited - (current != 0L ? now - current : 0L);
        }
    }

    private record ServerTimeCondition(
            PacketHandler handler,
            Clock clock,
            CompletableFuture<?> future,
            String stage,
            long limit,
            long wallLimit,
            BooleanSupplier condition)
            implements BooleanSupplier {

        @Override
        public boolean getAsBoolean() {
            if (future.isDone()) return true;
            ChannelConnection channel = handler.getChannel();
            long now = System.nanoTime();
            long used = clock.used(now);
            long elapsed = now - clock.started;
            if (used >= limit || elapsed >= wallLimit || !channel.isActive()) return condition.getAsBoolean();
            channel.setStageTimeout(
                    stage,
                    Duration.ofNanos(Math.min(limit - used, wallLimit - elapsed)),
                    this,
                    () -> handler.disconnect(Message.translation("client.general.disconnect.stageTimeout")));
            return true;
        }
    }
}
