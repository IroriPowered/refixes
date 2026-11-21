package local.hyinit.verify;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.OutgoingQueueLimits;
import cc.irori.refixes.early.duck.QuicheQueueOwner;
import cc.irori.refixes.early.util.NettyQueueBudget;
import cc.irori.refixes.early.util.QuicheQueueBudget;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.lib.quiche.QuicheChannel;
import com.hypixel.hytale.protocol.NetworkChannel;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.io.PacketIO;
import com.hypixel.hytale.protocol.io.PacketStatsRecorder;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import com.hypixel.hytale.server.core.io.netty.PacketArrayEncoder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.AttributeKey;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class QueueLimitsRegression {
    private static final long CONFIGURED_BYTES = 8192L;
    private static final int HEADER_BYTES = PacketIO.PACKET_HEADER_SIZE;
    private static final int TIMEOUT_SECONDS = 10;
    private static final int CONCURRENT_ROUNDS = 3;
    private static final long STALL_PART_BYTES = 131_072L;
    private static final long WRITER_PAUSE_NANOS = 20_000L;
    private static String check = "entry";

    private QueueLimitsRegression() {}

    public static void verify() {
        JsonObject result = new JsonObject();
        try {
            require(Thread.currentThread().getContextClassLoader().getClass().getName()
                            .equals("cc.irori.hyinit.mixin.HyinitClassLoader"),
                    "The fixture did not enter the real Hyinit classloader");
            assertConfigTable();
            check = "startup-limits";
            OutgoingQueueLimits active = EarlyOptions.outgoingQueueLimits();
            long maxBytes = Long.getLong("queue-limits.maxBytes", CONFIGURED_BYTES);
            require(active.maxBytes() == maxBytes && active.maxPackets() == Integer.MAX_VALUE,
                    "Startup did not apply OutgoingQueueMaxBytes=" + maxBytes + " and OutgoingQueueMaxPackets=0: "
                            + active);
            long broadcastWait =
                    Long.getLong("queue-limits.broadcastWaitMs", OutgoingQueueLimits.DEFAULT.broadcastWaitMs());
            require(active.broadcastWaitMs() == broadcastWait,
                    "Startup did not apply OutgoingQueueBroadcastWaitMs=" + broadcastWait + ": " + active);
            long broadcastStall =
                    Long.getLong("queue-limits.broadcastStallMs", OutgoingQueueLimits.DEFAULT.broadcastStallMs());
            require(active.broadcastStallMs() == broadcastStall,
                    "Startup did not apply OutgoingQueueBroadcastStallMs=" + broadcastStall + ": " + active);
            Integer parallelism = Integer.getInteger("queue-limits.commonParallelism");
            require(parallelism == null || ForkJoinPool.getCommonPoolParallelism() == parallelism,
                    "The common pool parallelism is " + ForkJoinPool.getCommonPoolParallelism() + ", not " + parallelism);
            if ("large".equals(System.getProperty("queue-limits.mode"))) {
                assertMixedNettyWriters();
                assertMixedQuicheWriters();
                assertNettyStall(broadcastStall, broadcastWait);
                assertQuicheStall(broadcastStall, broadcastWait);
            } else {
                assertNettyBoundary();
                assertQuicheBoundary();
                if (broadcastWait != 0L) {
                    assertConcurrentNettyBroadcasts();
                    assertConcurrentQuicheBroadcasts();
                }
                assertNettyBroadcast(broadcastWait);
                assertQuicheBroadcast(broadcastWait);
                assertLockedNettyBroadcast(broadcastWait);
                assertLockedQuicheBroadcast(broadcastWait);
            }
            result.addProperty("status", "passed");
            Files.writeString(Path.of("queue-limits-result.json"), result + "\n");
            System.out.println("[QueueLimitsRegression] PASS " + result);
            System.exit(0);
        } catch (Throwable failure) {
            result.addProperty("status", "failed");
            result.addProperty("check", check);
            result.addProperty("error", failure.toString());
            try {
                StringWriter trace = new StringWriter();
                failure.printStackTrace(new PrintWriter(trace));
                result.addProperty("trace", trace.toString());
                Files.writeString(Path.of("queue-limits-result.json"), result + "\n");
            } catch (Exception reportingFailure) {
                failure.addSuppressed(reportingFailure);
            }
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static void assertConfigTable() {
        check = "config-table";
        for (JsonObject config : List.of(new JsonObject(), early(null, null)))
            require(OutgoingQueueLimits.fromConfig(config).equals(OutgoingQueueLimits.DEFAULT),
                    "fromConfig(" + config + ") did not fall back to the defaults");
        int defaultPackets = OutgoingQueueLimits.DEFAULT.maxPackets();
        expectWait(broadcastWait(0L), 0L);
        expectWait(broadcastWait(Long.MAX_VALUE), Long.MAX_VALUE);
        expectStall(broadcastStall(0L), 0L);
        expectStall(broadcastStall(Long.MAX_VALUE), Long.MAX_VALUE);
        expect(early(1L, 1L), 1L, 1);
        expect(early(0L, null), Long.MAX_VALUE, defaultPackets);
        expect(early(0L, 0L), Long.MAX_VALUE, Integer.MAX_VALUE);
        expect(early(Long.MAX_VALUE, (long) Integer.MAX_VALUE), Long.MAX_VALUE, Integer.MAX_VALUE);
        JsonObject notObject = new JsonObject();
        notObject.addProperty("Early", 1);
        reject(notObject, "Early must be a JSON object");
        rejectValue("OutgoingQueueMaxBytes", new JsonPrimitive("8192"));
        rejectValue("OutgoingQueueMaxPackets", new JsonPrimitive(1.5));
        rejectValue("OutgoingQueueMaxPackets", new JsonPrimitive(Integer.MAX_VALUE + 1L));
        rejectValue("OutgoingQueueBroadcastWaitMs", new JsonPrimitive(-1));
        rejectValue("OutgoingQueueBroadcastStallMs", new JsonPrimitive(-1));
    }

    private static void rejectValue(String key, JsonElement value) {
        JsonObject config = early(null, null);
        config.getAsJsonObject("Early").add(key, value);
        reject(config, "Early." + key);
    }

    private static JsonObject early(Long bytes, Long packets) {
        JsonObject early = new JsonObject();
        if (bytes != null) early.addProperty("OutgoingQueueMaxBytes", bytes);
        if (packets != null) early.addProperty("OutgoingQueueMaxPackets", packets);
        JsonObject config = new JsonObject();
        config.add("Early", early);
        return config;
    }

    private static JsonObject broadcastWait(long waitMs) {
        JsonObject config = early(null, null);
        config.getAsJsonObject("Early").addProperty("OutgoingQueueBroadcastWaitMs", waitMs);
        return config;
    }

    private static JsonObject broadcastStall(long stallMs) {
        JsonObject config = early(null, null);
        config.getAsJsonObject("Early").addProperty("OutgoingQueueBroadcastStallMs", stallMs);
        return config;
    }

    private static void expect(JsonObject config, long bytes, int packets) {
        OutgoingQueueLimits limits = OutgoingQueueLimits.fromConfig(config);
        require(limits.maxBytes() == bytes && limits.maxPackets() == packets,
                "fromConfig(" + config + ") returned " + limits + ", expected " + bytes + "/" + packets);
    }

    private static void expectWait(JsonObject config, long waitMs) {
        OutgoingQueueLimits limits = OutgoingQueueLimits.fromConfig(config);
        require(limits.broadcastWaitMs() == waitMs,
                "fromConfig(" + config + ") returned " + limits + ", expected a broadcast wait of " + waitMs);
    }

    private static void expectStall(JsonObject config, long stallMs) {
        OutgoingQueueLimits limits = OutgoingQueueLimits.fromConfig(config);
        require(limits.broadcastStallMs() == stallMs,
                "fromConfig(" + config + ") returned " + limits + ", expected a broadcast stall of " + stallMs);
    }

    private static void reject(JsonObject config, String expected) {
        try {
            OutgoingQueueLimits limits = OutgoingQueueLimits.fromConfig(config);
            throw new AssertionError("fromConfig(" + config + ") accepted an invalid value: " + limits);
        } catch (IllegalStateException failure) {
            require(failure.getMessage().contains(expected),
                    "fromConfig(" + config + ") failed without naming " + expected + ": " + failure.getMessage());
        }
    }

    private static List<SizedPacket> boundary() {
        return List.of(packet(0, CONFIGURED_BYTES / 2), packet(1, CONFIGURED_BYTES / 2));
    }

    private static SizedPacket packet(int sequence, long frameBytes) {
        return new SizedPacket(sequence, Math.toIntExact(frameBytes - HEADER_BYTES));
    }

    private static void assertNettyBoundary() {
        check = "netty-byte-boundary";
        try (NettyFixture fixture = new NettyFixture()) {
            List<SizedPacket> boundary = boundary();
            for (SizedPacket packet : boundary) fixture.connection.write(packet);
            require(fixture.channel.isActive(), "Netty rejected the exact configured byte boundary");
            require(fixture.outbound.received().equals(boundary), "The byte boundary did not reach Netty intact");
            check = "netty-byte-overflow";
            fixture.connection.writeAndFlush(packet(boundary.size(), HEADER_BYTES));
            awaitNettyClose(fixture, "Netty did not close after its configured byte overflow");
            require(fixture.outbound.received().equals(boundary), "Netty admitted the byte-overflow packet");
        }
    }

    private static ToClientPacket[] partsOf(int first, int count) {
        ToClientPacket[] parts = new ToClientPacket[count];
        for (int i = 0; i < count; i++) parts[i] = packet(first + i, STALL_PART_BYTES);
        return parts;
    }

    private static long elapsedMs(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static void requireStallTime(long elapsed, long stallMs, long waitMs) {
        require(elapsed >= stallMs - 100L && elapsed < stallMs + 5_000L && elapsed < waitMs / 10L,
                "The broadcast gave up after " + elapsed + " ms, expected about the stall time of " + stallMs + " ms");
    }

    private static void stallWithPoolTask(Runnable waitingBroadcast, long stallMs, long waitMs) throws Exception {
        long start = System.nanoTime();
        CompletableFuture<Void> sent = CompletableFuture.runAsync(waitingBroadcast);
        Thread.sleep(200L);
        require(!sent.isDone(), "The broadcast did not wait for the stalled client");
        CompletableFuture.runAsync(() -> {}).get(stallMs / 2L, TimeUnit.MILLISECONDS);
        require(!sent.isDone(), "The broadcast stopped waiting before the second pool task ran");
        sent.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        requireStallTime(elapsedMs(start), stallMs, waitMs);
    }

    private static void runBroadcast(ToClientPacket[] asset, Consumer<ToClientPacket[]> write) {
        try {
            Method handler = Arrays.stream(CommonAssetModule.class.getDeclaredMethods())
                    .filter(method -> method.getName().contains("refixes$paceAssetBroadcast"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("The asset broadcast hook was not applied"));
            handler.setAccessible(true);
            Operation<Void> original = arguments -> {
                write.accept((ToClientPacket[]) arguments[1]);
                return null;
            };
            handler.invoke(allocate(CommonAssetModule.class), null, asset, original);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void assertNettyStall(long stallMs, long waitMs) throws Exception {
        check = "netty-broadcast-stall";
        List<ToClientPacket> backlog = List.of(partsOf(900, 4));
        try (NettyFixture fixture = new NettyFixture()) {
            for (ToClientPacket packet : backlog) fixture.connection.write(packet);
            stallWithPoolTask(() -> runBroadcast(partsOf(0, 5), fixture.connection::write), stallMs, waitMs);
            awaitNettyClose(fixture, "Netty did not apply the capped check after the stall");
            require(fixture.outbound.received().equals(backlog), "Netty admitted the asset of a stalled client");
        }
        check = "netty-broadcast-slow-drain";
        try (NettyFixture fixture = new NettyFixture()) {
            for (ToClientPacket packet : backlog) fixture.connection.write(packet);
            ToClientPacket[] asset = partsOf(0, 5);
            long start = System.nanoTime();
            CompletableFuture<Void> sent = broadcast(asset, fixture.connection::write);
            int next = 910;
            while (elapsedMs(start) < 3L * stallMs) {
                Thread.sleep(stallMs / 4L);
                require(!sent.isDone() && fixture.channel.isActive(),
                        "The broadcast gave up on a client that kept draining after " + elapsedMs(start) + " ms");
                fixture.connection.write(packet(next++, STALL_PART_BYTES));
                fixture.outbound.completeOldest();
            }
            fixture.outbound.complete();
            sent.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            QuicheQueueBudget pending = fixture.pending();
            List<SizedPacket> received = fixture.outbound.received();
            require(fixture.channel.isActive()
                            && received.subList(received.size() - asset.length, received.size()).equals(List.of(asset)),
                    "Netty did not deliver the asset to a client that kept draining");
            requireExempt(pending, 0L, 0, "An asset that fit under the limits was counted as exempt");
        }
    }

    private static void assertMixedNettyWriters() throws Exception {
        check = "netty-broadcast-mixed-writers";
        for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
            try (NettyFixture fixture = new NettyFixture()) {
                for (ToClientPacket packet : partsOf(900, 4)) fixture.connection.write(packet);
                ToClientPacket[] waiting = partsOf(0, 5);
                ToClientPacket[] locked = partsOf(100, 5);
                CompletableFuture<Void> sent = broadcast(waiting, fixture.connection::write);
                Thread.sleep(200L);
                require(!sent.isDone(), "The broadcast did not wait for the Netty queue to drain");
                fixture.outbound.complete();
                AtomicBoolean writing = new AtomicBoolean(true);
                Thread ordinary = Thread.ofPlatform().start(() -> {
                    int sequence = 10_000;
                    while (writing.get()) {
                        fixture.connection.write(packet(sequence++, HEADER_BYTES));
                        LockSupport.parkNanos(WRITER_PAUSE_NANOS);
                    }
                });
                try {
                    lockedBroadcast(locked, fixture.connection::write);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
                    while (!sent.isDone()) {
                        require(System.nanoTime() < deadline, "The Netty broadcast did not finish");
                        fixture.outbound.complete();
                        Thread.yield();
                    }
                    sent.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } finally {
                    writing.set(false);
                    ordinary.join();
                }
                require(fixture.channel.isActive(), "Mixed writers closed the Netty connection");
                requireContiguous(fixture.outbound.received(), waiting, locked);
            }
        }
    }

    private static void assertMixedQuicheWriters() throws Exception {
        check = "quiche-broadcast-mixed-writers";
        for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
            try (QuicheFixture fixture = new QuicheFixture()) {
                for (ToClientPacket packet : partsOf(900, 4)) fixture.channel.write(packet);
                ToClientPacket[] waiting = partsOf(0, 5);
                ToClientPacket[] locked = partsOf(100, 5);
                CompletableFuture<Void> sent = broadcast(waiting, fixture.channel::write);
                Thread.sleep(200L);
                require(!sent.isDone(), "The broadcast did not wait for the Quiche queue to drain");
                List<ToClientPacket> delivered = new ArrayList<>(fixture.drain());
                AtomicBoolean writing = new AtomicBoolean(true);
                Thread ordinary = Thread.ofPlatform().start(() -> {
                    int sequence = 10_000;
                    while (writing.get()) {
                        fixture.channel.write(packet(sequence++, HEADER_BYTES));
                        LockSupport.parkNanos(WRITER_PAUSE_NANOS);
                    }
                });
                try {
                    lockedBroadcast(locked, fixture.channel::write);
                    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
                    while (!sent.isDone()) {
                        require(System.nanoTime() < deadline, "The Quiche broadcast did not finish");
                        delivered.addAll(fixture.drain());
                        Thread.yield();
                    }
                    sent.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } finally {
                    writing.set(false);
                    ordinary.join();
                }
                delivered.addAll(fixture.drain());
                require(!fixture.budget.overflowed(), "Mixed writers closed the Quiche connection");
                requireContiguous(delivered, waiting, locked);
            }
        }
    }

    private static void assertQuicheStall(long stallMs, long waitMs) throws Exception {
        check = "quiche-broadcast-stall";
        List<ToClientPacket> backlog = List.of(partsOf(900, 4));
        try (QuicheFixture fixture = new QuicheFixture()) {
            for (ToClientPacket packet : backlog) fixture.channel.write(packet);
            stallWithPoolTask(() -> runBroadcast(partsOf(0, 5), fixture.channel::write), stallMs, waitMs);
            require(fixture.budget.overflowed(), "Quiche did not apply the capped check after the stall");
            require(fixture.queued().equals(backlog), "Quiche admitted the asset of a stalled client");
        }
        check = "quiche-broadcast-slow-drain";
        try (QuicheFixture fixture = new QuicheFixture()) {
            for (ToClientPacket packet : backlog) fixture.channel.write(packet);
            ToClientPacket[] asset = partsOf(0, 5);
            long start = System.nanoTime();
            CompletableFuture<Void> sent = broadcast(asset, fixture.channel::write);
            int next = 910;
            while (elapsedMs(start) < 3L * stallMs) {
                Thread.sleep(stallMs / 4L);
                require(!sent.isDone() && !fixture.budget.overflowed(),
                        "The broadcast gave up on a client that kept draining after " + elapsedMs(start) + " ms");
                fixture.channel.write(packet(next++, STALL_PART_BYTES));
                fixture.drain(1);
            }
            fixture.drain(backlog.size());
            sent.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            require(!fixture.budget.overflowed() && fixture.queued().equals(List.of(asset)),
                    "Quiche did not deliver the asset to a client that kept draining");
            requireExempt(fixture.budget, 0L, 0, "An asset that fit under the limits was counted as exempt");
        }
    }

    private static void assertQuicheBoundary() throws Exception {
        check = "quiche-byte-boundary";
        try (QuicheFixture fixture = new QuicheFixture()) {
            List<SizedPacket> boundary = boundary();
            fixture.channel.write(boundary.toArray(ToClientPacket[]::new));
            require(!fixture.budget.overflowed() && fixture.queued().equals(boundary),
                    "Quiche rejected the exact configured byte boundary");
            require(fixture.budget.pendingBytes() == CONFIGURED_BYTES && fixture.budget.pendingPackets() == 2,
                    "Quiche did not account the queued boundary");
            check = "quiche-close-exempt";
            SizedPacket disconnect = packet(boundary.size(), HEADER_BYTES);
            require(fixture.closeAdd(disconnect), "A close-path packet was refused on a full queue");
            require(!fixture.budget.overflowed(), "A close-path packet tripped the queue limit");
            List<ToClientPacket> expected = new ArrayList<>(boundary);
            expected.add(disconnect);
            require(fixture.queued().equals(expected), "The close-path packet was not queued behind the boundary");
            check = "quiche-byte-overflow";
            fixture.channel.writeAndFlush(packet(boundary.size() + 1, HEADER_BYTES));
            require(fixture.budget.overflowed(), "Quiche admitted the first write above its configured byte cap");
            require(fixture.queued().equals(expected), "A Quiche overflow packet reached the packet queue");
            require(!fixture.channel.isWritable(), "Quiche remained writable after a hard overflow");
        }
    }

    private static ToClientPacket[] broadcastAsset() {
        return broadcastAsset(0);
    }

    private static ToClientPacket[] broadcastAsset(int first) {
        ToClientPacket[] asset = new ToClientPacket[4];
        for (int i = 0; i < asset.length; i++) asset[i] = packet(first + i, CONFIGURED_BYTES / 2);
        return asset;
    }

    private static List<ToClientPacket> backlog() {
        return List.of(packet(900, CONFIGURED_BYTES / 2), packet(901, CONFIGURED_BYTES / 2));
    }

    private static List<ToClientPacket> concat(List<ToClientPacket> head, ToClientPacket[] tail) {
        List<ToClientPacket> all = new ArrayList<>(head);
        all.addAll(List.of(tail));
        return all;
    }

    private static CompletableFuture<Void> broadcast(ToClientPacket[] asset, Consumer<ToClientPacket[]> write) {
        return CompletableFuture.runAsync(() -> runBroadcast(asset, write));
    }

    private static void requireContiguous(List<? extends ToClientPacket> received, ToClientPacket[]... assets) {
        for (ToClientPacket[] asset : assets) {
            int start = received.indexOf(asset[0]);
            require(start >= 0
                            && start + asset.length <= received.size()
                            && received.subList(start, start + asset.length).equals(List.of(asset)),
                    "Two broadcasts were interleaved on one connection: " + received);
        }
    }

    private static void assertConcurrentNettyBroadcasts() throws Exception {
        check = "netty-broadcast-contiguous";
        for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
            try (NettyFixture fixture = new NettyFixture()) {
                for (ToClientPacket packet : backlog()) fixture.connection.write(packet);
                ToClientPacket[] first = broadcastAsset(100);
                ToClientPacket[] second = broadcastAsset(200);
                CompletableFuture<Void> one = broadcast(first, fixture.connection::write);
                CompletableFuture<Void> two = broadcast(second, fixture.connection::write);
                Thread.sleep(200L);
                require(!one.isDone() && !two.isDone() && fixture.outbound.received().size() == 2,
                        "The two Netty broadcasts did not wait for the queue to drain");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
                while (!one.isDone() || !two.isDone()) {
                    require(System.nanoTime() < deadline, "The two Netty broadcasts did not finish");
                    fixture.outbound.complete();
                    Thread.sleep(5L);
                }
                one.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                two.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                require(fixture.channel.isActive(), "Two Netty broadcasts closed the connection");
                List<SizedPacket> received = fixture.outbound.received();
                require(received.size() == 10, "The two Netty broadcasts were not delivered in full: " + received);
                requireContiguous(received, first, second);
            }
        }
    }

    private static void assertConcurrentQuicheBroadcasts() throws Exception {
        check = "quiche-broadcast-contiguous";
        for (int round = 0; round < CONCURRENT_ROUNDS; round++) {
            try (QuicheFixture fixture = new QuicheFixture()) {
                for (ToClientPacket packet : backlog()) fixture.channel.write(packet);
                ToClientPacket[] first = broadcastAsset(100);
                ToClientPacket[] second = broadcastAsset(200);
                CompletableFuture<Void> one = broadcast(first, fixture.channel::write);
                CompletableFuture<Void> two = broadcast(second, fixture.channel::write);
                Thread.sleep(200L);
                require(!one.isDone() && !two.isDone() && fixture.queued().size() == 2,
                        "The two Quiche broadcasts did not wait for the queue to drain");
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
                List<ToClientPacket> delivered = new ArrayList<>();
                while (!one.isDone() || !two.isDone()) {
                    require(System.nanoTime() < deadline, "The two Quiche broadcasts did not finish");
                    delivered.addAll(fixture.drain());
                    Thread.sleep(5L);
                }
                one.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                two.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                delivered.addAll(fixture.drain());
                require(!fixture.budget.overflowed(), "Two Quiche broadcasts closed the connection");
                require(delivered.size() == 10, "The two Quiche broadcasts were not delivered in full: " + delivered);
                requireContiguous(delivered, first, second);
            }
        }
    }

    private static void assertNettyBroadcast(long waitMs) throws Exception {
        check = waitMs == 0L ? "netty-broadcast-no-wait" : "netty-broadcast-wait";
        try (NettyFixture fixture = new NettyFixture()) {
            ToClientPacket[] asset = broadcastAsset();
            if (waitMs == 0L) {
                broadcast(asset, fixture.connection::write).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                awaitNettyClose(fixture, "Netty did not close after a broadcast above its byte cap without a wait");
                require(fixture.outbound.received().isEmpty(), "Netty admitted a broadcast above its byte cap");
                return;
            }
            for (ToClientPacket packet : backlog()) fixture.connection.write(packet);
            CompletableFuture<Void> sent = broadcast(asset, fixture.connection::write);
            Thread.sleep(200L);
            require(!sent.isDone() && fixture.outbound.received().size() == 2,
                    "The broadcast did not wait for the Netty queue to drain");
            fixture.outbound.complete();
            sent.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            QuicheQueueBudget pending = fixture.pending();
            require(fixture.channel.isActive(), "The broadcast closed the Netty connection");
            require(fixture.outbound.received().equals(concat(backlog(), asset)),
                    "The broadcast did not reach Netty in one piece after the drain");
            requireExempt(pending, 4 * (CONFIGURED_BYTES / 2), 4, "A drained broadcast above the cap was not exempt");
            fixture.outbound.complete();
            requireExempt(pending, 0L, 0, "Netty did not release the drained broadcast");
            require(pending.pendingBytes() == 0L, "Netty did not release the drained broadcast");
        }
    }

    private static void lockedBroadcast(ToClientPacket[] asset, Consumer<ToClientPacket[]> write) throws Exception {
        AssetRegistry.ASSET_LOCK.writeLock().lock();
        try {
            broadcast(asset, write).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            AssetRegistry.ASSET_LOCK.writeLock().unlock();
        }
    }

    private static void requireExempt(QuicheQueueBudget budget, long bytes, int packets, String message) {
        require(budget.exemptBytes() == bytes && budget.exemptPackets() == packets, message + ": exempt "
                + budget.exemptBytes() + "/" + budget.exemptPackets() + ", expected " + bytes + "/" + packets);
    }

    private static void assertLockedNettyBroadcast(long waitMs) throws Exception {
        check = waitMs == 0L ? "netty-asset-lock-no-wait" : "netty-asset-lock-exempt";
        ToClientPacket[] asset = broadcastAsset();
        long assetBytes = asset.length * (CONFIGURED_BYTES / 2);
        try (NettyFixture fixture = new NettyFixture()) {
            lockedBroadcast(asset, fixture.connection::write);
            if (waitMs == 0L) {
                awaitNettyClose(fixture, "A broadcast under the asset write lock did not close without a wait");
                require(fixture.outbound.received().isEmpty(), "Netty admitted a broadcast above its byte cap");
                return;
            }
            QuicheQueueBudget pending = fixture.pending();
            require(fixture.channel.isActive() && fixture.outbound.received().equals(List.of(asset)),
                    "Netty did not deliver the exempt broadcast in full");
            requireExempt(pending, assetBytes, asset.length, "Netty did not count the broadcast as exempt");
            check = "netty-asset-lock-exempt-cap";
            SizedPacket full = packet(asset.length, CONFIGURED_BYTES);
            fixture.connection.write(full);
            require(fixture.channel.isActive(), "Netty refused a write within the cap behind an exempt broadcast");
            fixture.connection.writeAndFlush(packet(asset.length + 1, HEADER_BYTES));
            awaitNettyClose(fixture, "Netty admitted a write above the cap behind an exempt broadcast");
            List<ToClientPacket> expected = new ArrayList<>(List.of(asset));
            expected.add(full);
            require(fixture.outbound.received().equals(expected), "Netty admitted the write above the cap");
            check = "netty-asset-lock-exempt-close";
            fixture.outbound.failRemaining();
            requireExempt(pending, 0L, 0, "Netty close did not release the exempt broadcast");
            require(pending.pendingBytes() == 0L && pending.pendingPackets() == 0,
                    "Netty close did not release queued reservations");
        }
        check = "netty-asset-lock-exempt-drain";
        try (NettyFixture fixture = new NettyFixture()) {
            lockedBroadcast(asset, fixture.connection::write);
            QuicheQueueBudget pending = fixture.pending();
            requireExempt(pending, assetBytes, asset.length, "Netty did not count the broadcast as exempt");
            fixture.outbound.complete();
            requireExempt(pending, 0L, 0, "Netty did not release the exempt broadcast after the drain");
            require(fixture.channel.isActive() && pending.pendingBytes() == 0L && pending.pendingPackets() == 0,
                    "Netty did not release the drained broadcast");
        }
    }

    private static void assertLockedQuicheBroadcast(long waitMs) throws Exception {
        check = waitMs == 0L ? "quiche-asset-lock-no-wait" : "quiche-asset-lock-exempt";
        ToClientPacket[] asset = broadcastAsset();
        long assetBytes = asset.length * (CONFIGURED_BYTES / 2);
        try (QuicheFixture fixture = new QuicheFixture()) {
            lockedBroadcast(asset, fixture.channel::write);
            if (waitMs == 0L) {
                require(fixture.budget.overflowed(), "A Quiche broadcast under the asset write lock did not close");
                require(fixture.queued().isEmpty(), "Quiche admitted a broadcast above its byte cap");
                return;
            }
            require(!fixture.budget.overflowed() && fixture.queued().equals(List.of(asset)),
                    "Quiche did not deliver the exempt broadcast in full");
            requireExempt(fixture.budget, assetBytes, asset.length, "Quiche did not count the broadcast as exempt");
            check = "quiche-asset-lock-exempt-cap";
            SizedPacket full = packet(asset.length, CONFIGURED_BYTES);
            fixture.channel.writeAndFlush(full);
            require(!fixture.budget.overflowed(), "Quiche refused a write within the cap behind an exempt broadcast");
            List<ToClientPacket> expected = new ArrayList<>(List.of(asset));
            expected.add(full);
            fixture.channel.writeAndFlush(packet(asset.length + 1, HEADER_BYTES));
            require(fixture.budget.overflowed(), "Quiche admitted a write above the cap behind an exempt broadcast");
            require(fixture.queued().equals(expected), "Quiche queued the write above the cap");
            check = "quiche-asset-lock-exempt-close";
        }
        check = "quiche-asset-lock-exempt-drain";
        try (QuicheFixture fixture = new QuicheFixture()) {
            lockedBroadcast(asset, fixture.channel::write);
            requireExempt(fixture.budget, assetBytes, asset.length, "Quiche did not count the broadcast as exempt");
            require(fixture.drain().equals(List.of(asset)), "Quiche did not drain the exempt broadcast intact");
            requireExempt(fixture.budget, 0L, 0, "Quiche did not release the exempt broadcast after the drain");
            require(!fixture.budget.overflowed() && fixture.budget.pendingBytes() == 0L,
                    "Quiche did not release the drained broadcast");
        }
    }

    private static void awaitNettyClose(NettyFixture fixture, String message) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (!fixture.channel.closeFuture().isDone()) {
            require(System.nanoTime() < deadline, message);
            fixture.channel.runPendingTasks();
            fixture.channel.runScheduledPendingTasks();
            Thread.yield();
        }
    }

    private static void assertQuicheBroadcast(long waitMs) throws Exception {
        check = waitMs == 0L ? "quiche-broadcast-no-wait" : "quiche-broadcast-wait";
        try (QuicheFixture fixture = new QuicheFixture()) {
            ToClientPacket[] asset = broadcastAsset();
            if (waitMs == 0L) {
                broadcast(asset, fixture.channel::write).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                require(fixture.budget.overflowed(), "Quiche admitted a broadcast above its byte cap without a wait");
                require(fixture.queued().isEmpty(), "A Quiche broadcast above its byte cap reached the packet queue");
                return;
            }
            for (ToClientPacket packet : backlog()) fixture.channel.write(packet);
            CompletableFuture<Void> sent = broadcast(asset, fixture.channel::write);
            Thread.sleep(200L);
            require(!sent.isDone() && fixture.queued().size() == 2,
                    "The broadcast did not wait for the Quiche queue to drain");
            List<ToClientPacket> delivered = new ArrayList<>(fixture.drain(backlog().size()));
            sent.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            require(!fixture.budget.overflowed() && fixture.channel.isActive(), "The broadcast closed Quiche");
            require(fixture.queued().equals(List.of(asset)), "The broadcast did not reach Quiche in one piece");
            requireExempt(fixture.budget, 4 * (CONFIGURED_BYTES / 2), 4, "A drained broadcast above the cap was not exempt");
            delivered.addAll(fixture.drain());
            require(delivered.equals(concat(backlog(), asset)), "The broadcast did not drain from Quiche intact");
            requireExempt(fixture.budget, 0L, 0, "Quiche did not release the drained broadcast");
        }
    }

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record SizedPacket(int sequence, int size) implements ToClientPacket {
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
            throw new AssertionError("The queue-limit fixture must not serialize payloads or send traffic");
        }

        @Override
        public int computeSize() {
            return size;
        }
    }

    private static final class NettyFixture implements AutoCloseable {
        private final RetainingOutbound outbound = new RetainingOutbound();
        private final EmbeddedChannel channel =
                new EmbeddedChannel(outbound, new PacketArrayEncoder(), new SerialWrites());
        private final NettyUtil.NettyChannelConnection connection = new NettyUtil.NettyChannelConnection(channel);

        private QuicheQueueBudget pending() throws Exception {
            NettyQueueBudget budget = channel.attr(AttributeKey.<NettyQueueBudget>valueOf("refixes.quic.queueBudget"))
                    .get();
            Field field = NettyQueueBudget.class.getDeclaredField("pending");
            field.setAccessible(true);
            return (QuicheQueueBudget) field.get(budget);
        }

        @Override
        public void close() {
            channel.close().syncUninterruptibly();
            outbound.failRemaining();
            channel.releaseInbound();
            channel.releaseOutbound();
        }
    }

    private static final class SerialWrites extends ChannelOutboundHandlerAdapter {
        @Override
        public synchronized void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
            context.write(message, promise);
        }
    }

    private static final class RetainingOutbound extends ChannelOutboundHandlerAdapter {
        private final List<ChannelPromise> pending = new ArrayList<>();
        private final List<SizedPacket> received = new ArrayList<>();

        @Override
        public synchronized void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
            require(message instanceof SizedPacket, "Unexpected Netty outbound message: " + message);
            require(!promise.isVoid(), "Netty did not retain a tracked completion promise");
            pending.add(promise);
            received.add((SizedPacket) message);
        }

        @Override
        public void flush(ChannelHandlerContext context) {}

        private synchronized void failRemaining() {
            for (ChannelPromise promise : pending) promise.tryFailure(new ClosedChannelException());
            pending.clear();
        }

        private void completeOldest() {
            ChannelPromise oldest;
            synchronized (this) {
                if (pending.isEmpty()) return;
                oldest = pending.remove(0);
            }
            oldest.trySuccess();
        }

        private void complete() {
            List<ChannelPromise> done;
            synchronized (this) {
                done = List.copyOf(pending);
                pending.clear();
            }
            for (ChannelPromise promise : done) promise.trySuccess();
        }

        private synchronized List<SizedPacket> received() {
            return List.copyOf(received);
        }
    }

    private static final class QuicheFixture implements AutoCloseable {
        private final Object connection;
        private final QuicheQueueBudget budget = new QuicheQueueBudget();
        private final QuicheChannel channel;

        private QuicheFixture() throws Exception {
            Class<?> connectionType = Class.forName("com.hypixel.hytale.lib.quiche.QuicheConnection");
            connection = allocate(connectionType);
            set(connectionType, connection, "active", new AtomicBoolean(true));
            set(connectionType, connection, "processingThread", Thread.ofPlatform().unstarted(() -> {
                throw new AssertionError("The Quiche fixture must not start a native processing loop");
            }));
            Field ownerBudget = Arrays.stream(connectionType.getDeclaredFields())
                    .filter(field -> field.getType() == QuicheQueueBudget.class).findFirst().orElseThrow();
            ownerBudget.setAccessible(true);
            ownerBudget.set(connection, budget);
            require(((QuicheQueueOwner) connection).refixes$queueBudget() == budget,
                    "The Quiche connection did not expose its shared queue budget");
            Constructor<QuicheChannel> constructor = QuicheChannel.class.getDeclaredConstructor(
                    connectionType, long.class, NetworkChannel.class, PacketStatsRecorder.class);
            constructor.setAccessible(true);
            channel = constructor.newInstance(connection, 0L, NetworkChannel.Default, null);
            require(channel.isActive(), "The constructed Quiche channel was not active");
        }

        @SuppressWarnings("unchecked")
        private ConcurrentLinkedQueue<ToClientPacket> queue() throws Exception {
            Field field = QuicheChannel.class.getDeclaredField("packetQueue");
            field.setAccessible(true);
            return (ConcurrentLinkedQueue<ToClientPacket>) field.get(channel);
        }

        private List<ToClientPacket> queued() throws Exception {
            return List.copyOf(queue());
        }

        private boolean closeAdd(ToClientPacket packet) throws Exception {
            ConcurrentLinkedQueue<ToClientPacket> queue = queue();
            Method handler = Arrays.stream(QuicheChannel.class.getDeclaredMethods())
                    .filter(method -> method.getName().contains("refixes$accountCloseAdd"))
                    .findFirst()
                    .orElseThrow();
            handler.setAccessible(true);
            Operation<Boolean> original = arguments -> queue.add((ToClientPacket) arguments[1]);
            return (Boolean) handler.invoke(channel, queue, packet, original);
        }

        private List<ToClientPacket> drain() throws Exception {
            return drain(Integer.MAX_VALUE);
        }

        private List<ToClientPacket> drain(int limit) throws Exception {
            ConcurrentLinkedQueue<ToClientPacket> queue = queue();
            Method handler = Arrays.stream(QuicheChannel.class.getDeclaredMethods())
                    .filter(method -> method.getName().contains("refixes$accountPoll"))
                    .findFirst()
                    .orElseThrow();
            handler.setAccessible(true);
            Operation<Object> original = arguments -> queue.poll();
            List<ToClientPacket> drained = new ArrayList<>();
            for (Object packet; drained.size() < limit && (packet = handler.invoke(channel, queue, original)) != null; )
                drained.add((ToClientPacket) packet);
            return drained;
        }

        @Override
        public void close() throws Exception {
            Method close = QuicheChannel.class.getDeclaredMethod("close");
            close.setAccessible(true);
            close.invoke(channel);
            set(connection.getClass(), connection, "active", new AtomicBoolean(false));
            require(queued().isEmpty() && budget.pendingBytes() == 0 && budget.pendingPackets() == 0,
                    "Quiche close did not release queued reservations");
            require(budget.exemptBytes() == 0 && budget.exemptPackets() == 0,
                    "Quiche close did not release the exempt broadcast");
        }

        private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }
    }
}
