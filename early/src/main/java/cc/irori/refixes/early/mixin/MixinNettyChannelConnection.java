package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.QuicheDrainSignal;
import cc.irori.refixes.early.util.IncidentCounters;
import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.NettyQueueBudget;
import cc.irori.refixes.early.util.SetupAssetPacing;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.io.ChannelConnection;
import com.hypixel.hytale.protocol.io.PacketIO;
import com.hypixel.hytale.protocol.packets.connection.QuicApplicationErrorCode;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NettyUtil.NettyChannelConnection.class)
public abstract class MixinNettyChannelConnection implements QuicheDrainSignal {

    @Unique
    private static final AttributeKey<NettyQueueBudget> refixes$QUEUE_BUDGET = NettyQueueBudget.ATTRIBUTE;

    @Unique
    private static final AttributeKey<Boolean> refixes$QUEUE_REGISTERED =
            AttributeKey.valueOf("refixes.quic.queueRegistered");

    @Unique
    private static final CompletableFuture<Void> refixes$DRAINED = CompletableFuture.completedFuture(null);

    @Unique
    private volatile NettyQueueBudget refixes$queueBudget;

    @Shadow
    public abstract Channel channel();

    @Override
    public boolean refixes$backlogged() {
        Channel channel = channel();
        return channel.isActive() && refixes$budget(channel).backlogged();
    }

    @Override
    public CompletableFuture<Void> refixes$drained() {
        Channel channel = channel();
        NettyQueueBudget budget = refixes$budget(channel);
        budget.flush();
        return channel.isActive() ? budget.drained() : refixes$DRAINED;
    }

    @WrapOperation(
            method = {
                "write(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "write([Lcom/hypixel/hytale/protocol/ToClientPacket;)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lio/netty/channel/Channel;write(Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)Lio/netty/channel/ChannelFuture;"),
            require = 2,
            allow = 2)
    private ChannelFuture refixes$boundWrite(
            Channel channel, Object message, ChannelPromise promise, Operation<ChannelFuture> original) {
        return refixes$write(channel, message, promise, original, false);
    }

    @WrapOperation(
            method = {
                "writeAndFlush(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush([Lcom/hypixel/hytale/protocol/ToClientPacket;)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lio/netty/channel/Channel;writeAndFlush(Ljava/lang/Object;Lio/netty/channel/ChannelPromise;)Lio/netty/channel/ChannelFuture;"),
            require = 2,
            allow = 2)
    private ChannelFuture refixes$boundWriteAndFlush(
            Channel channel, Object message, ChannelPromise promise, Operation<ChannelFuture> original) {
        return refixes$write(channel, message, promise, original, true);
    }

    @Unique
    private ChannelFuture refixes$write(
            Channel channel, Object message, ChannelPromise promise, Operation<ChannelFuture> original, boolean flush) {
        NettyQueueBudget budget = refixes$budget(channel);
        ChannelConnection connection = (ChannelConnection) (Object) this;
        boolean paced = SetupAssetPacing.nettyWorker(budget);
        boolean broadcast = !paced && SetupAssetPacing.broadcastWaits(budget);
        boolean exempt = !paced && !broadcast && SetupAssetPacing.broadcastExempt();
        if (paced && !channel.isActive()) {
            try {
                SetupAssetPacing.reserveNetty(connection, budget, 0L, 0);
            } catch (RuntimeException | Error failure) {
                promise.tryFailure(failure);
                throw failure;
            }
        }
        if (budget.failure() != null) return refixes$reject(channel, promise, budget);
        if (paced && message instanceof ToClientPacket[] batch)
            return refixes$writeBatch(connection, channel, batch, promise, budget, flush);
        long bytes = 0L;
        int packets = 0;
        try {
            if (message instanceof ToClientPacket packet) {
                bytes = refixes$frameBytes(packet, budget);
                if (bytes < 0L) return refixes$reject(channel, promise, budget);
                packets = 1;
            } else if (message instanceof ToClientPacket[] batch) {
                for (ToClientPacket packet : batch) {
                    if (packet == null) continue;
                    long size = refixes$frameBytes(packet, budget);
                    if (size < 0L) return refixes$reject(channel, promise, budget);
                    bytes += size;
                    packets++;
                }
            }
        } catch (Error failure) {
            promise.tryFailure(failure);
            throw failure;
        }
        if (packets == 0) return original.call(channel, message, promise);
        boolean reserved = false;
        try {
            if (broadcast && SetupAssetPacing.awaitNettyBroadcast(budget)) {
                reserved = budget.tryReserveWithinLimits(bytes, packets);
                exempt = !reserved;
            }
            if (!reserved)
                reserved = exempt
                        ? budget.reserveExempt(bytes, packets)
                        : paced
                                ? SetupAssetPacing.reserveNetty(connection, budget, bytes, packets)
                                : budget.reserve(bytes, packets);
        } catch (RuntimeException | Error failure) {
            promise.tryFailure(failure);
            throw failure;
        }
        if (!reserved) return refixes$reject(channel, promise, budget);
        ChannelPromise tracked = null;
        try {
            tracked = refixes$tracked(channel, budget, bytes, packets, promise, null, exempt);
            return original.call(channel, message, tracked);
        } catch (RuntimeException | Error failure) {
            if (tracked == null) {
                if (exempt) budget.releaseExempt(bytes, packets, failure);
                else budget.release(bytes, packets, failure);
                promise.tryFailure(failure);
            } else tracked.tryFailure(failure);
            throw failure;
        }
    }

    @Unique
    private static ChannelFuture refixes$writeBatch(
            ChannelConnection connection,
            Channel channel,
            ToClientPacket[] batch,
            ChannelPromise promise,
            NettyQueueBudget budget,
            boolean flush) {
        ChannelPromise aggregate = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        aggregate.addListener(result -> {
            if (result.isSuccess()) promise.trySuccess();
            else promise.tryFailure(result.cause());
        });
        NettyQueueBudget.Batch writes = new NettyQueueBudget.Batch(aggregate);
        try {
            for (ToClientPacket packet : batch) {
                if (packet == null) continue;
                long bytes = refixes$frameBytes(packet, budget);
                if (bytes < 0L) return refixes$reject(channel, aggregate, budget);
                if (!SetupAssetPacing.reserveNetty(connection, budget, bytes, 1))
                    return refixes$reject(channel, aggregate, budget);
                writes.add();
                ChannelPromise tracked = null;
                try {
                    tracked = refixes$tracked(channel, budget, bytes, 1, null, writes, false);
                    channel.write(packet, tracked);
                } catch (RuntimeException | Error failure) {
                    if (tracked == null) {
                        budget.release(bytes, 1, failure);
                        writes.complete(failure);
                    } else tracked.tryFailure(failure);
                    throw failure;
                }
            }
            return aggregate;
        } catch (RuntimeException | Error failure) {
            aggregate.tryFailure(failure);
            throw failure;
        } finally {
            try {
                if (flush) channel.flush();
            } catch (RuntimeException | Error failure) {
                aggregate.tryFailure(failure);
                throw failure;
            } finally {
                writes.finish();
            }
        }
    }

    @Unique
    private static ChannelPromise refixes$tracked(
            Channel channel,
            NettyQueueBudget budget,
            long bytes,
            int packets,
            ChannelPromise promise,
            NettyQueueBudget.Batch batch,
            boolean exempt) {
        ChannelPromise tracked = new DefaultChannelPromise(channel, ImmediateEventExecutor.INSTANCE);
        tracked.addListener(result -> {
            if (exempt) budget.releaseExempt(bytes, packets, result.cause());
            else budget.release(bytes, packets, result.cause());
            if (batch != null) batch.complete(result.cause());
            if (promise == null) return;
            if (result.isSuccess()) promise.trySuccess();
            else promise.tryFailure(result.cause());
        });
        return tracked;
    }

    @Unique
    private NettyQueueBudget refixes$budget(Channel channel) {
        NettyQueueBudget cached = refixes$queueBudget;
        if (cached != null) return cached;
        Channel owner = channel.parent() instanceof QuicChannel ? channel.parent() : channel;
        var attribute = owner.attr(refixes$QUEUE_BUDGET);
        NettyQueueBudget budget = attribute.get();
        if (budget == null) {
            NettyQueueBudget created = new NettyQueueBudget(owner);
            NettyQueueBudget previous = attribute.setIfAbsent(created);
            budget = previous == null ? created : previous;
        }
        if (owner.attr(refixes$QUEUE_REGISTERED).setIfAbsent(Boolean.TRUE) == null) budget.register(owner);
        if (channel != owner && channel.attr(refixes$QUEUE_REGISTERED).setIfAbsent(Boolean.TRUE) == null)
            budget.register(channel);
        refixes$queueBudget = budget;
        return budget;
    }

    @Unique
    private static ChannelFuture refixes$reject(Channel channel, ChannelPromise promise, NettyQueueBudget budget) {
        Throwable failure = budget.failure();
        if (failure == null) failure = new ClosedChannelException();
        promise.tryFailure(failure);
        if (failure instanceof ClosedChannelException || !budget.requestClose()) return promise;
        if (failure instanceof IllegalStateException) {
            IncidentCounters.NETTY_QUEUE_LIMIT_DISCONNECTS.increment();
            Logs.logger().atWarning().log("%s for %s", failure.getMessage(), channel.remoteAddress());
        }
        FormattedMessage reason = new FormattedMessage();
        reason.rawText = failure instanceof IllegalArgumentException
                ? "Invalid outgoing packet size"
                : "Outgoing packet queue limit exceeded";
        NettyUtil.closeApplicationConnection(channel, QuicApplicationErrorCode.NoError, reason);
        return promise;
    }

    @Unique
    private static long refixes$frameBytes(ToClientPacket packet, NettyQueueBudget budget) {
        int size;
        try {
            size = packet.computeSize();
        } catch (RuntimeException cause) {
            IllegalArgumentException failure = new IllegalArgumentException(
                    "Invalid outgoing packet size for " + packet.getClass().getName(), cause);
            if (budget.fail(failure))
                Logs.logger()
                        .atWarning()
                        .withCause(cause)
                        .log(
                                "Invalid outgoing packet size for %s",
                                packet.getClass().getName());
            return -1L;
        }
        if (size < 0) {
            IllegalArgumentException failure = new IllegalArgumentException("Invalid outgoing packet size " + size
                    + " for " + packet.getClass().getName());
            if (budget.fail(failure))
                Logs.logger()
                        .atWarning()
                        .withCause(failure)
                        .log(
                                "Invalid outgoing packet size for %s: computeSize returned %d",
                                packet.getClass().getName(), size);
            return -1L;
        }
        return (long) size + PacketIO.PACKET_HEADER_SIZE;
    }
}
