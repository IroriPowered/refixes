package cc.irori.refixes.early.util;

import cc.irori.refixes.early.duck.QuicheDrainSignal;
import cc.irori.refixes.early.duck.QuicheQueueOwner;
import com.hypixel.hytale.lib.quiche.QuicheChannel;
import com.hypixel.hytale.protocol.io.ChannelConnection;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.common.events.SendCommonAssetsEvent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import java.time.Duration;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SetupAssetPacing {

    private static final long WALL_CLOCK_TIMEOUT_MULTIPLIER = 10L;
    private static final ThreadLocal<PacketHandler> OWNER = new ThreadLocal<>();
    private static final CancellationException CLOSED = new CancellationException();
    private static final Map<PacketHandler, Clock> CLOCKS = new WeakHashMap<>();

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
