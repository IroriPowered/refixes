package local.hyinit.verify;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.OutgoingQueueLimits;
import cc.irori.refixes.early.duck.QuicheQueueOwner;
import cc.irori.refixes.early.util.QuicheQueueBudget;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.hypixel.hytale.lib.quiche.QuicheChannel;
import com.hypixel.hytale.protocol.NetworkChannel;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.io.PacketIO;
import com.hypixel.hytale.protocol.io.PacketStatsRecorder;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import com.hypixel.hytale.server.core.io.netty.PacketArrayEncoder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.foreign.MemorySegment;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class QueueLimitsRegression {
    private static final long CONFIGURED_BYTES = 8192L;
    private static final int HEADER_BYTES = PacketIO.PACKET_HEADER_SIZE;
    private static final int TIMEOUT_SECONDS = 10;
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
            require(active.maxBytes() == CONFIGURED_BYTES && active.maxPackets() == Integer.MAX_VALUE,
                    "Startup did not apply OutgoingQueueMaxBytes=8192 and OutgoingQueueMaxPackets=0: " + active);
            assertNettyBoundary();
            assertQuicheBoundary();
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
        long defaultBytes = OutgoingQueueLimits.DEFAULT.maxBytes();
        int defaultPackets = OutgoingQueueLimits.DEFAULT.maxPackets();
        require(defaultBytes == 67_108_864L && defaultPackets == 65_536, "Defaults changed");
        expect(new JsonObject(), defaultBytes, defaultPackets);
        expect(early(null, null), defaultBytes, defaultPackets);
        expect(early(8192L, null), 8192L, defaultPackets);
        expect(early(null, 2L), defaultBytes, 2);
        expect(early(1L, 1L), 1L, 1);
        expect(early(0L, null), Long.MAX_VALUE, defaultPackets);
        expect(early(null, 0L), defaultBytes, Integer.MAX_VALUE);
        expect(early(0L, 0L), Long.MAX_VALUE, Integer.MAX_VALUE);
        expect(early(Long.MAX_VALUE, (long) Integer.MAX_VALUE), Long.MAX_VALUE, Integer.MAX_VALUE);
        JsonObject notObject = new JsonObject();
        notObject.addProperty("Early", 1);
        reject(notObject, "Early must be a JSON object");
        for (String key : List.of("OutgoingQueueMaxBytes", "OutgoingQueueMaxPackets")) {
            BigInteger maximum = BigInteger.valueOf(key.endsWith("Bytes") ? Long.MAX_VALUE : Integer.MAX_VALUE);
            for (JsonElement value : List.of(
                    new JsonPrimitive(-1),
                    new JsonPrimitive(1.5),
                    new JsonPrimitive("8192"),
                    new JsonPrimitive(true),
                    JsonNull.INSTANCE,
                    new JsonArray(),
                    new JsonObject(),
                    new JsonPrimitive(maximum.add(BigInteger.ONE)))) {
                JsonObject config = new JsonObject();
                JsonObject early = new JsonObject();
                early.add(key, value);
                config.add("Early", early);
                reject(config, "Early." + key);
            }
        }
    }

    private static JsonObject early(Long bytes, Long packets) {
        JsonObject early = new JsonObject();
        if (bytes != null) early.addProperty("OutgoingQueueMaxBytes", bytes);
        if (packets != null) early.addProperty("OutgoingQueueMaxPackets", packets);
        JsonObject config = new JsonObject();
        config.add("Early", early);
        return config;
    }

    private static void expect(JsonObject config, long bytes, int packets) {
        OutgoingQueueLimits limits = OutgoingQueueLimits.fromConfig(config);
        require(limits.maxBytes() == bytes && limits.maxPackets() == packets,
                "fromConfig(" + config + ") returned " + limits + ", expected " + bytes + "/" + packets);
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
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
            while (!fixture.channel.closeFuture().isDone()) {
                require(System.nanoTime() < deadline, "Netty did not close after its configured byte overflow");
                fixture.channel.runPendingTasks();
                fixture.channel.runScheduledPendingTasks();
                Thread.yield();
            }
            require(fixture.outbound.received().equals(boundary), "Netty admitted the byte-overflow packet");
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
        private final EmbeddedChannel channel = new EmbeddedChannel(outbound, new PacketArrayEncoder());
        private final NettyUtil.NettyChannelConnection connection = new NettyUtil.NettyChannelConnection(channel);

        @Override
        public void close() {
            channel.close().syncUninterruptibly();
            outbound.failRemaining();
            channel.releaseInbound();
            channel.releaseOutbound();
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
            Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
            Field singleton = unsafeType.getDeclaredField("theUnsafe");
            singleton.setAccessible(true);
            connection = unsafeType.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), connectionType);
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

        @Override
        public void close() throws Exception {
            Method close = QuicheChannel.class.getDeclaredMethod("close");
            close.setAccessible(true);
            close.invoke(channel);
            set(connection.getClass(), connection, "active", new AtomicBoolean(false));
            require(queued().isEmpty() && budget.pendingBytes() == 0 && budget.pendingPackets() == 0,
                    "Quiche close did not release queued reservations");
        }

        private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }
    }
}
