package local.hyinit.verify;

import static local.hyinit.verify.RegressionRunner.require;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.OutgoingQueueLimits;
import cc.irori.refixes.early.duck.QuicheDrainSignal;
import cc.irori.refixes.early.util.NettyQueueBudget;
import cc.irori.refixes.early.util.SetupAssetPacing;
import com.hypixel.hytale.logger.backend.HytaleLoggerBackend;
import com.hypixel.hytale.protocol.Asset;
import com.hypixel.hytale.protocol.NetworkChannel;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.ToServerPacket;
import com.hypixel.hytale.protocol.io.PacketIO;
import com.hypixel.hytale.server.core.asset.common.events.SendCommonAssetsEvent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.ProtocolVersion;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import com.hypixel.hytale.server.core.io.netty.PacketArrayEncoder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.foreign.MemorySegment;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public final class NettySetupPacingRegression {
    private static final OutgoingQueueLimits LIMITS = EarlyOptions.outgoingQueueLimits();
    private static final int FRAME_BYTES = 1024 * 1024;
    private static final int TIMEOUT_SECONDS = 10;

    private NettySetupPacingRegression() {}

    public static void assertPatched() throws Exception {
        assertSetupBurst(false, true);
        assertWorkerWriteFlow();
        assertSetupBurst(true, true);
        assertSetupBurst(true, false);
        assertCloseUnblocksWorker();
        assertInterruptFailsActiveWorker();
        assertConcurrentSetupReservation();
        assertEventLoopDoesNotWait();
        assertPacketLimit();
        assertInvalidSize(false);
        assertInvalidSize(true);
        assertFailedWriteReleasesCapacity();
        System.out.println("[LocalVerify] Netty setup pacing regressions PASS");
    }

    private static void assertWorkerWriteFlow() throws Exception {
        try (Fixture fixture = new Fixture()) {
            require((Object) fixture.connection instanceof QuicheDrainSignal,
                    "Native Netty setup connections must participate in setup pacing");
            List<SizedPacket> packets = packets(4);
            AtomicReference<Thread> worker = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<Void> work = SetupAssetPacing.continueOnWorker(
                    fixture.handler, CompletableFuture.completedFuture(packets), values -> {
                        worker.set(Thread.currentThread());
                        started.countDown();
                        for (SizedPacket packet : values) fixture.connection.write(packet);
                        fixture.connection.flush();
                    });
            await(started, "Standalone setup writer did not start");
            fixture.outbound.firstFlush.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertBlocked(fixture, worker.get(), work);
            fixture.outbound.drainAndResume();
            work.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            fixture.assertDelivered(packets);
            require(fixture.channel.isActive(), "Standalone setup write flow disconnected");
        }
    }

    private static void assertSetupBurst(boolean batch, boolean flush) throws Exception {
        try (Fixture fixture = new Fixture()) {
            List<SizedPacket> packets = packets(128);
            AtomicReference<Thread> worker = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(1);
            SendCommonAssetsEvent event = new SendCommonAssetsEvent(fixture.handler, new Asset[0]);
            CompletableFuture<SendCommonAssetsEvent> work = SetupAssetPacing.chain(value -> {
                worker.set(Thread.currentThread());
                started.countDown();
                if (batch) {
                    ToClientPacket[] values = packets.toArray(ToClientPacket[]::new);
                    if (flush) fixture.connection.writeAndFlush(values);
                    else fixture.connection.write(values);
                } else {
                    for (SizedPacket packet : packets) fixture.connection.writeAndFlush(packet);
                }
                fixture.connection.flush();
            }).apply(CompletableFuture.completedFuture(event));
            await(started, "Setup asset worker did not start");
            fixture.outbound.firstFlush.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertBlocked(fixture, worker.get(), work);
            require(fixture.outbound.received().size() < packets.size(),
                    "The complete setup burst was queued before its retained writes drained");
            fixture.outbound.drainAndResume();
            work.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            fixture.assertDelivered(packets);
            require(fixture.channel.isActive(),
                    "A legitimate setup " + (batch ? "batch" : "burst") + " exceeding 64 MiB disconnected");
        }
    }

    private static void assertCloseUnblocksWorker() throws Exception {
        try (Fixture fixture = new Fixture()) {
            AtomicReference<Thread> worker = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(1);
            SendCommonAssetsEvent event = new SendCommonAssetsEvent(fixture.handler, new Asset[0]);
            CompletableFuture<SendCommonAssetsEvent> work = SetupAssetPacing.chain(value -> {
                worker.set(Thread.currentThread());
                started.countDown();
                for (SizedPacket packet : packets(8)) fixture.connection.write(packet);
            }).apply(CompletableFuture.completedFuture(event));
            await(started, "Closing setup worker did not start");
            fixture.outbound.firstFlush.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertBlocked(fixture, worker.get(), work);
            CompletableFuture<Void> drained = fixture.signal().refixes$drained();
            require(!drained.isDone(), "Retained setup writes were reported drained before close");
            fixture.channel.close().syncUninterruptibly();
            drained.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            work.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            require(!fixture.channel.isActive(), "Close did not deactivate the stalled native channel");
            require(!fixture.outbound.hasSuccessfulWrites(),
                    "The close-unblock fixture unexpectedly drained its retained messages");
        }
    }

    private static void assertInterruptFailsActiveWorker() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SizedPacket retained = packet(0);
            fixture.connection.write(retained);
            AtomicReference<Thread> worker = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<Boolean> interrupted = new CompletableFuture<>();
            SendCommonAssetsEvent event = new SendCommonAssetsEvent(fixture.handler, new Asset[0]);
            CompletableFuture<SendCommonAssetsEvent> work = SetupAssetPacing.chain(value -> {
                worker.set(Thread.currentThread());
                started.countDown();
                try {
                    fixture.connection.writeAndFlush(packet(1));
                } finally {
                    interrupted.complete(Thread.currentThread().isInterrupted());
                }
            }).apply(CompletableFuture.completedFuture(event));
            await(started, "Interruptible setup worker did not start");
            fixture.outbound.firstFlush.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertBlocked(fixture, worker.get(), work);
            worker.get().interrupt();
            try {
                work.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                throw new AssertionError("Interrupting an active setup worker incorrectly completed setup successfully");
            } catch (ExecutionException | CancellationException expected) {
                require(interrupted.get(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        "The active setup worker did not preserve its interruption status");
            }
            require(fixture.channel.isActive(), "Interrupting a setup worker closed its active native channel");
            require(fixture.outbound.received().equals(List.of(retained)),
                    "An interrupted setup write was admitted without its pending capacity");
            require(!fixture.outbound.hasSuccessfulWrites(),
                    "The interrupt fixture drained a retained write before interruption completed");
            fixture.outbound.drainAndResume();
            fixture.assertDelivered(List.of(retained));
        }
    }

    private static void assertConcurrentSetupReservation() throws Exception {
        try (Fixture fixture = new Fixture()) {
            NettyQueueBudget budget = new NettyQueueBudget(fixture.channel);
            budget.register(fixture.channel);
            long setupBytes = LIMITS.maxBytes() - 128 * 1024;
            long competingBytes = 256 * 1024;
            AtomicReference<Thread> worker = new AtomicReference<>();
            CountDownLatch checkedAvailable = new CountDownLatch(1);
            CountDownLatch competitorReserved = new CountDownLatch(1);
            CountDownLatch setupReserved = new CountDownLatch(1);
            CountDownLatch releaseSetup = new CountDownLatch(1);
            CompletableFuture<Void> work = SetupAssetPacing.continueOnWorker(
                    fixture.handler, CompletableFuture.completedFuture(null), ignored -> {
                        worker.set(Thread.currentThread());
                        require(!budget.backlogged(setupBytes, 1),
                                "An empty connection budget could not fit a legal setup frame");
                        checkedAvailable.countDown();
                        try {
                            await(competitorReserved, "The competing stream did not reserve its capacity");
                            require(SetupAssetPacing.reserveNetty(fixture.connection, budget, setupBytes, 1),
                                    "A setup reservation failed instead of waiting for its competing stream");
                            setupReserved.countDown();
                            await(releaseSetup, "The admitted setup reservation was not released");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("The atomic reservation worker was unexpectedly interrupted", interrupted);
                        } finally {
                            if (setupReserved.getCount() == 0) budget.release(setupBytes, 1, null);
                        }
                    });
            await(checkedAvailable, "The setup worker did not observe initially available capacity");
            require(budget.reserve(competingBytes, 1), "The competing stream could not reserve below the soft cap");
            require(!budget.backlogged(), "The competing stream alone exceeded the soft pacing threshold");
            competitorReserved.countDown();
            try {
                fixture.outbound.firstFlush.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                awaitCondition(() -> {
                    Thread.State state = worker.get().getState();
                    return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING || work.isDone();
                }, "The contended setup reservation did not wait for the competing stream");
                require(!work.isDone() && setupReserved.getCount() == 1,
                        "A setup frame was admitted without enough remaining hard-cap capacity");
                require(budget.failure() == null,
                        "Losing capacity after the availability check poisoned the connection budget");
                CompletableFuture<Void> drained = budget.drained(setupBytes, 1);
                require(!drained.isDone(), "The competing reservation did not hold the setup drain future");
                budget.release(competingBytes, 1, null);
                drained.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                await(setupReserved, "The setup reservation was not admitted after the competing stream drained");
                require(!budget.tryReserve(competingBytes, 1),
                        "A successful setup admission did not reserve its capacity atomically");
                require(budget.failure() == null, "Retrying unavailable paced capacity marked a hard overflow");
            } finally {
                releaseSetup.countDown();
            }
            work.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            require(budget.tryReserve(LIMITS.maxBytes(), LIMITS.maxPackets()),
                    "Contended setup admission leaked or reserved its capacity more than once");
            budget.release(LIMITS.maxBytes(), LIMITS.maxPackets(), null);
            require(fixture.channel.isActive(), "Contended setup admission disconnected the native channel");
        }
    }

    private static void assertEventLoopDoesNotWait() throws Exception {
        try (Fixture fixture = new Fixture()) {
            SizedPacket first = packet(0);
            SizedPacket second = packet(1);
            fixture.connection.write(first);
            fixture.outbound.firstWrite.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            require(fixture.signal().refixes$backlogged(), "Event-loop fixture did not create a real backlog");
            CompletableFuture<Void> returned = new CompletableFuture<>();
            CompletableFuture<Void> followingTask = new CompletableFuture<>();
            fixture.channel.eventLoop().execute(() -> {
                try {
                    require(fixture.channel.eventLoop().inEventLoop(), "Write did not run on the Netty event loop");
                    fixture.connection.writeAndFlush(second);
                    SetupAssetPacing.awaitDrain(fixture.handler);
                    returned.complete(null);
                } catch (Throwable failure) {
                    returned.completeExceptionally(failure);
                }
            });
            fixture.channel.eventLoop().execute(() -> followingTask.complete(null));
            Thread runner = Thread.ofPlatform().daemon().name("Refixes-NettyRegressionEventLoop").start(
                    fixture.channel::runPendingTasks);
            returned.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            followingTask.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            require(!fixture.outbound.hasSuccessfulWrites(),
                    "Event-loop promptness was tested only after the backlog had drained");
            fixture.outbound.drainAndResume();
            runner.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
            require(!runner.isAlive(), "Embedded Netty event-loop runner did not finish");
            fixture.assertDelivered(List.of(first, second));
            require(fixture.channel.isActive(), "An event-loop write disconnected below the hard cap");
        }
    }

    private static void assertPacketLimit() throws Exception {
        try (Fixture fixture = new Fixture(); Diagnostics diagnostics = new Diagnostics()) {
            SizedPacket packet = new SizedPacket(0, 0, null);
            ToClientPacket[] boundary = new ToClientPacket[LIMITS.maxPackets()];
            Arrays.fill(boundary, packet);
            fixture.connection.write(boundary);
            fixture.outbound.firstWrite.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            require(fixture.channel.isActive(), "The packet-count limit rejected its exact boundary");
            require(fixture.outbound.received().equals(Arrays.asList(boundary)),
                    "The packet-count boundary batch was not passed intact to Netty");
            fixture.connection.write(packet);
            require(fixture.channel.closeFuture().await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "An unpaced packet-count overflow did not finish closing the native channel");
            require(!fixture.channel.isActive(), "An unpaced packet-count overflow did not close the channel");
            require(fixture.outbound.received().equals(Arrays.asList(boundary)),
                    "The packet-count overflow packet reached the native outbound handler");
            diagnostics.assertClassification("packet");
        }
    }

    private static void assertInvalidSize(boolean throwing) throws Exception {
        try (Fixture fixture = new Fixture(); Diagnostics diagnostics = new Diagnostics()) {
            RuntimeException failure = throwing ? new IllegalStateException("controlled size failure") : null;
            SizedPacket invalid = new SizedPacket(0, throwing ? 16 : -1, failure);
            if (throwing) fixture.connection.write(new ToClientPacket[] {invalid});
            else fixture.connection.writeAndFlush(invalid);
            require(fixture.channel.closeFuture().await(TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "Invalid packet sizing did not finish closing the native channel");
            require(!fixture.channel.isActive(), "An invalid packet size did not close the native channel");
            require(fixture.outbound.received().isEmpty(), "An invalid-size packet reached the outbound handler");
            diagnostics.assertClassification("invalid");
        }
    }

    private static void assertFailedWriteReleasesCapacity() throws Exception {
        try (Fixture fixture = new Fixture()) {
            List<SizedPacket> initial = packets((int) (LIMITS.maxBytes() / FRAME_BYTES));
            for (SizedPacket packet : initial) fixture.connection.writeAndFlush(packet);
            fixture.outbound.firstFlush.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            CompletableFuture<Void> drained = fixture.signal().refixes$drained();
            require(!drained.isDone(), "A retained full-capacity write must hold its drain future");
            IllegalStateException failure = new IllegalStateException("controlled outbound failure");
            fixture.outbound.failFirst(failure);
            try {
                fixture.channel.checkException();
            } catch (IllegalStateException reported) {
                require(reported == failure, "Netty propagated an unexpected outbound failure");
            }
            drained.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            SizedPacket replacement = packet(initial.size());
            fixture.connection.writeAndFlush(replacement);
            require(fixture.channel.isActive(), "A failed write kept its byte budget reserved");
            List<SizedPacket> received = new ArrayList<>(initial);
            received.add(replacement);
            require(fixture.outbound.received().equals(received),
                    "The next write was not admitted after the failed write");
            fixture.outbound.drainAndResume();
            List<SizedPacket> succeeded = new ArrayList<>(initial.subList(1, initial.size()));
            succeeded.add(replacement);
            require(fixture.outbound.succeeded().equals(succeeded),
                    "The replacement write did not complete after the failed write released its capacity");
            require(fixture.outbound.failures() == 1, "The controlled write failure was not preserved");
            require(!fixture.signal().refixes$backlogged(), "Replacement completion did not release the budget");
        }
    }

    private static void assertBlocked(Fixture fixture, Thread worker, CompletableFuture<?> work) throws Exception {
        require(fixture.channel.isActive(),
                "A legitimate setup burst disconnected instead of waiting for retained Netty writes");
        require(worker != null && worker.isVirtual(), "Setup pacing did not use its real virtual worker");
        awaitCondition(() -> {
            Thread.State state = worker.getState();
            return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING || work.isDone();
        }, "The setup worker did not actually wait for retained Netty write promises");
        require(!work.isDone(), "The setup worker completed instead of waiting for transport drainage");
        require(fixture.channel.isActive(), "A paced setup worker disconnected while waiting for drainage");
        require(fixture.signal().refixes$backlogged(), "The blocked worker has no actual transport backlog");
        require(!fixture.signal().refixes$drained().isDone(), "The stalled drain future completed without a drain");
        require(fixture.outbound.hasPendingWrites(), "No actual Netty write promises are retained");
        require(!fixture.outbound.hasSuccessfulWrites(), "The stall fixture already completed its write promises");
    }

    private static List<SizedPacket> packets(int count) {
        List<SizedPacket> packets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) packets.add(packet(i));
        return packets;
    }

    private static SizedPacket packet(int sequence) {
        return new SizedPacket(sequence, FRAME_BYTES - PacketIO.PACKET_HEADER_SIZE, null);
    }

    private static void await(CountDownLatch latch, String message) throws InterruptedException {
        require(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), message);
    }

    private static void awaitCondition(BooleanSupplier condition, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!condition.getAsBoolean()) {
            require(System.nanoTime() < deadline, message);
            Thread.yield();
        }
    }

    private record SizedPacket(int sequence, int size, RuntimeException failure) implements ToClientPacket {
        @Override
        public int getId() {
            return 2;
        }

        @Override
        public NetworkChannel getChannel() {
            return NetworkChannel.Default;
        }

        @Override
        public int serialize(MemorySegment target, int offset) {
            int length = computeSize();
            target.asSlice(offset, length).fill((byte) sequence);
            return length;
        }

        @Override
        public int computeSize() {
            if (failure != null) throw failure;
            return size;
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final RetainingOutbound outbound = new RetainingOutbound();
        private final EmbeddedChannel channel = new EmbeddedChannel(outbound, new PacketArrayEncoder());
        private final NettyUtil.NettyChannelConnection connection = new NettyUtil.NettyChannelConnection(channel);
        private final PacketHandler handler = new TestPacketHandler(connection);

        private QuicheDrainSignal signal() {
            require((Object) connection instanceof QuicheDrainSignal,
                    "The real transformed Netty connection does not expose setup drainage");
            return (QuicheDrainSignal) (Object) connection;
        }

        private void assertDelivered(List<SizedPacket> packets) throws Exception {
            outbound.drainAndResume();
            require(outbound.received().equals(packets), "Netty changed the setup packet order or contents");
            require(outbound.succeeded().equals(packets), "Setup packets did not complete in their original order");
            require(!outbound.hasPendingWrites(), "Setup packets left retained Netty write promises");
            require(outbound.failures() == 0, "A legitimate setup packet failed its Netty write");
            signal().refixes$drained().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            require(!signal().refixes$backlogged(), "Completed Netty writes left the setup budget backlogged");
        }

        @Override
        public void close() {
            channel.close().syncUninterruptibly();
            outbound.failRemaining(new ClosedChannelException());
            channel.releaseInbound();
            channel.releaseOutbound();
        }
    }

    private static final class TestPacketHandler extends PacketHandler {
        private TestPacketHandler(NettyUtil.NettyChannelConnection channel) {
            super(channel, new ProtocolVersion(0));
        }

        @Override
        public String getIdentifier() {
            return "NettySetupPacingRegression";
        }

        @Override
        public void accept(ToServerPacket packet) {
            throw new AssertionError("The outbound-only fixture received an inbound packet");
        }
    }

    private static final class RetainingOutbound extends ChannelOutboundHandlerAdapter {
        private final List<Pending> pending = new ArrayList<>();
        private final List<SizedPacket> received = new ArrayList<>();
        private final List<SizedPacket> succeeded = new ArrayList<>();
        private final CompletableFuture<Void> firstWrite = new CompletableFuture<>();
        private final CompletableFuture<Void> firstFlush = new CompletableFuture<>();
        private boolean autoDrain;
        private int failures;

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
            require(message instanceof SizedPacket, "Unexpected native outbound message: " + message);
            List<SizedPacket> packets = List.of((SizedPacket) message);
            synchronized (this) {
                require(!promise.isVoid(), "The transformed Netty connection did not track write completion");
                pending.add(new Pending(packets, promise));
                received.addAll(packets);
            }
            firstWrite.complete(null);
        }

        @Override
        public void flush(ChannelHandlerContext context) {
            List<Pending> ready;
            synchronized (this) {
                for (Pending write : pending) write.flushed = true;
                ready = autoDrain ? removeFlushed() : List.of();
            }
            firstFlush.complete(null);
            succeed(ready);
        }

        private void drainAndResume() {
            List<Pending> ready;
            synchronized (this) {
                autoDrain = true;
                ready = removeFlushed();
            }
            succeed(ready);
        }

        private List<Pending> removeFlushed() {
            List<Pending> ready = new ArrayList<>();
            pending.removeIf(write -> {
                if (!write.flushed) return false;
                ready.add(write);
                return true;
            });
            return ready;
        }

        private void succeed(List<Pending> ready) {
            for (Pending write : ready) {
                synchronized (this) {
                    succeeded.addAll(write.packets);
                }
                require(write.promise.trySuccess(), "A retained Netty promise completed before explicit drainage");
            }
        }

        private void failFirst(Throwable cause) {
            Pending write;
            synchronized (this) {
                require(!pending.isEmpty(), "No Netty write was available to fail");
                write = pending.removeFirst();
                failures++;
            }
            require(write.promise.tryFailure(cause), "The controlled outbound write was already complete");
        }

        private void failRemaining(Throwable cause) {
            List<Pending> abandoned;
            synchronized (this) {
                abandoned = List.copyOf(pending);
                pending.clear();
            }
            for (Pending write : abandoned) write.promise.tryFailure(cause);
        }

        private synchronized List<SizedPacket> received() {
            return List.copyOf(received);
        }

        private synchronized List<SizedPacket> succeeded() {
            return List.copyOf(succeeded);
        }

        private synchronized boolean hasSuccessfulWrites() {
            return !succeeded.isEmpty();
        }

        private synchronized boolean hasPendingWrites() {
            return !pending.isEmpty();
        }

        private synchronized int failures() {
            return failures;
        }
    }

    private static final class Pending {
        private final List<SizedPacket> packets;
        private final ChannelPromise promise;
        private boolean flushed;

        private Pending(List<SizedPacket> packets, ChannelPromise promise) {
            this.packets = packets;
            this.promise = promise;
        }
    }

    private static final class Diagnostics implements AutoCloseable {
        private final CopyOnWriteArrayList<LogRecord> records = new CopyOnWriteArrayList<>();

        private Diagnostics() {
            HytaleLoggerBackend.subscribe(records);
        }

        private void assertClassification(String expected) {
            List<LogRecord> warnings = records.stream()
                    .filter(record -> record.getLevel().intValue() >= Level.WARNING.intValue())
                    .filter(record -> record.getLoggerName() != null && record.getLoggerName().contains("Refixes"))
                    .toList();
            require(warnings.size() == 1, "A native queue rejection must produce one diagnostic: " + warnings);
            String text = warnings.getFirst().getMessage().toLowerCase(Locale.ROOT);
            if (expected.equals("invalid")) {
                require(text.contains("invalid"), "Invalid packet sizes were misreported as queue pressure: " + text);
                require(!text.matches("(?s).*\\b(?:byte|packet)[ -]?(?:count )?(?:cap|limit)\\b.*"),
                        "An invalid-size diagnostic incorrectly claimed a queue cap: " + text);
            } else {
                require(!text.contains("invalid"), "A legitimate hard overflow was misreported as an invalid size: " + text);
                require(text.matches("(?s).*\\b" + expected + "s?(?:[ -]?(?:count|budget))?[ -]?(?:cap|limit)\\b.*"),
                        "A native hard overflow did not identify its " + expected + " cap: " + text);
            }
        }

        @Override
        public void close() {
            HytaleLoggerBackend.unsubscribe(records);
        }
    }
}
