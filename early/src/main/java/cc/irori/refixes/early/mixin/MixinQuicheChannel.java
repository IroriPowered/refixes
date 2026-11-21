package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.QuicheDrainSignal;
import cc.irori.refixes.early.duck.QuicheQueueOwner;
import cc.irori.refixes.early.duck.QuicheTransportWritable;
import cc.irori.refixes.early.util.QuicheQueueBudget;
import cc.irori.refixes.early.util.SetupAssetPacing;
import com.hypixel.hytale.lib.quiche.QuicheChannel;
import com.hypixel.hytale.lib.quiche.QuicheNative;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.NetworkChannel;
import com.hypixel.hytale.protocol.PacketRegistry;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.io.ConnectionHandler;
import com.hypixel.hytale.protocol.io.PacketIO;
import com.hypixel.hytale.protocol.io.PacketStatsRecorder;
import com.hypixel.hytale.protocol.io.ProtocolException;
import com.hypixel.hytale.protocol.packets.connection.QuicApplicationErrorCode;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(QuicheChannel.class)
public abstract class MixinQuicheChannel implements QuicheTransportWritable, QuicheDrainSignal, QuicheQueueOwner {

    @Unique
    private static final long refixes$UNWRITABLE_BACKLOG_BYTES = 524288L;

    @Unique
    private static final Set<String> refixes$INVALID_SIZES = ConcurrentHashMap.newKeySet();

    @Unique
    private final Object refixes$queueLock = new Object();

    @Unique
    private final LongArrayFIFOQueue refixes$queuedSizes = new LongArrayFIFOQueue();

    @Unique
    private long refixes$queuedBytes;

    @Unique
    private QuicheQueueBudget refixes$queueBudget;

    @Unique
    private long refixes$backlogBytes;

    @Unique
    private CompletableFuture<Void> refixes$drainWaiter;

    @Unique
    private volatile boolean refixes$sendDead;

    @Unique
    private boolean refixes$sentData;

    @Unique
    private boolean refixes$localShutdown;

    @Shadow
    @Final
    private long streamId;

    @Shadow
    @Final
    private NetworkChannel networkChannel;

    @Shadow
    private int sendOffset;

    @Shadow
    private int sendLimit;

    @Shadow
    @Final
    private ConcurrentLinkedQueue<ToClientPacket> packetQueue;

    @Shadow
    @Final
    private AtomicBoolean closed;

    @Shadow
    @Final
    private Arena arena;

    @Shadow
    @Final
    private static HytaleLogger LOGGER;

    @Shadow
    @Final
    private MemorySegment packetBuffer;

    @Shadow
    private int currentPacketOffset;

    @Shadow
    private int currentPacketSize;

    @Shadow
    private Arena tempPacketArena;

    @Shadow
    private MemorySegment tempPacketBuffer;

    @Shadow
    @Final
    private AtomicBoolean writable;

    @Shadow
    private void freeTempSendBuffer() {}

    @Inject(method = "<init>", at = @At("RETURN"), require = 1, allow = 1)
    private void refixes$shareQueueBudget(
            @Coerce Object connection,
            long stream,
            NetworkChannel channel,
            PacketStatsRecorder recorder,
            CallbackInfo ci) {
        refixes$queueBudget = ((QuicheQueueOwner) connection).refixes$queueBudget();
        refixes$backlogBytes = Math.min(refixes$UNWRITABLE_BACKLOG_BYTES, refixes$queueBudget.maxBytes());
    }

    @Override
    public QuicheQueueBudget refixes$queueBudget() {
        return refixes$queueBudget;
    }

    @Unique
    private boolean refixes$reserve(long bytes, int packets) {
        if (refixes$queueBudget.reserve(bytes, packets)) return true;
        ((QuicheChannel) (Object) this).flush();
        return false;
    }

    @Unique
    private static long refixes$frameBytes(ToClientPacket packet) {
        int size;
        try {
            size = packet.computeSize();
        } catch (RuntimeException failure) {
            if (packet != null && refixes$INVALID_SIZES.add(packet.getClass().getName()))
                LOGGER.atWarning().withCause(failure).log(
                        "Invalid outgoing packet size for %s", packet.getClass().getName());
            return Long.MAX_VALUE;
        }
        if (size < 0) {
            if (refixes$INVALID_SIZES.add(packet.getClass().getName()))
                LOGGER.atWarning().log(
                        "Invalid outgoing packet size for %s: computeSize returned %d",
                        packet.getClass().getName(), size);
            return Long.MAX_VALUE;
        }
        return (long) size + PacketIO.PACKET_HEADER_SIZE;
    }

    @Unique
    private void refixes$clearQueue() {
        CompletableFuture<Void> waiter;
        synchronized (refixes$queueLock) {
            int queued = refixes$queuedSizes.size();
            long exemptBytes = 0L;
            int exemptPackets = 0;
            while (!refixes$queuedSizes.isEmpty()) {
                long size = refixes$queuedSizes.dequeueLong();
                if (size < 0L) {
                    exemptBytes -= size;
                    exemptPackets++;
                }
            }
            refixes$queueBudget.releaseExempt(exemptBytes, exemptPackets);
            refixes$queueBudget.release(refixes$queuedBytes - exemptBytes, queued - exemptPackets);
            packetQueue.clear();
            refixes$queuedSizes.clear();
            refixes$queuedSizes.trim();
            refixes$queuedBytes = 0L;
            waiter = refixes$drainWaiter;
            refixes$drainWaiter = null;
        }
        if (waiter != null) waiter.complete(null);
    }

    @Override
    public boolean refixes$backlogged() {
        synchronized (refixes$queueLock) {
            return refixes$queuedBytes >= refixes$backlogBytes;
        }
    }

    @Override
    public CompletableFuture<Void> refixes$drained() {
        synchronized (refixes$queueLock) {
            if (closed.get() || refixes$queueBudget.overflowed() || refixes$queuedBytes < refixes$backlogBytes)
                return CompletableFuture.completedFuture(null);
            if (refixes$drainWaiter == null) refixes$drainWaiter = new CompletableFuture<>();
            return refixes$drainWaiter;
        }
    }

    @Override
    public boolean refixes$isTransportWritable() {
        return writable.get();
    }

    @Unique
    private boolean refixes$requiredStream() {
        return networkChannel == NetworkChannel.Default
                || networkChannel == NetworkChannel.Chunks
                || networkChannel == NetworkChannel.WorldMap;
    }

    @Override
    public boolean refixes$probeAfterRecv(MemorySegment connection) {
        if (closed.get()
                || refixes$sendDead
                || (!refixes$sentData && networkChannel != NetworkChannel.Voice)
                || refixes$localShutdown
                || writable.get()) return false;
        long capacity = QuicheNative.connStreamCapacity(connection, streamId);
        if (capacity == QuicheNative.QuicheError.STREAM_STOPPED.code()
                || capacity == QuicheNative.QuicheError.INVALID_STREAM_STATE.code()) {
            refixes$stopSending(connection, capacity);
            return refixes$requiredStream();
        }
        return false;
    }

    @Unique
    private void refixes$stopSending(MemorySegment connection, long error) {
        if (QuicheNative.connIsClosed(connection) || QuicheNative.connIsDraining(connection)) return;
        synchronized (refixes$queueLock) {
            if (refixes$sendDead) return;
            refixes$sendDead = true;
        }
        boolean required = refixes$requiredStream();
        if (required)
            QuicheNative.connClose(
                    connection, true, QuicApplicationErrorCode.NoError.getValue(), MemorySegment.NULL, 0);
        writable.set(false);
        sendOffset = 0;
        sendLimit = 0;
        freeTempSendBuffer();
        refixes$clearQueue();
        if (required)
            LOGGER.atWarning().log(
                    "Peer stopped stream %d (%s), closing connection: %d", streamId, networkChannel, error);
    }

    @WrapOperation(
            method = "tryWrite",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/lib/quiche/QuicheNative;connStreamSend(Ljava/lang/foreign/MemorySegment;JLjava/lang/foreign/MemorySegment;JZLjava/lang/foreign/MemorySegment;)J"),
            require = 1,
            allow = 1)
    private long refixes$detectStoppedSend(
            MemorySegment connection,
            long id,
            MemorySegment buffer,
            long length,
            boolean fin,
            MemorySegment error,
            Operation<Long> original) {
        long sent = original.call(connection, id, buffer, length, fin, error);
        if (sent > 0) {
            refixes$sentData = true;
            refixes$queueBudget.sent(sent);
            return sent;
        }
        if (refixes$localShutdown) return sent;
        if (sent == QuicheNative.QuicheError.STREAM_STOPPED.code()) {
            refixes$stopSending(connection, sent);
            return QuicheNative.QuicheError.DONE.code();
        }
        if (sent == QuicheNative.QuicheError.DONE.code()
                && (refixes$sentData || networkChannel == NetworkChannel.Voice)
                && !QuicheNative.connIsClosed(connection)
                && !QuicheNative.connIsDraining(connection)) {
            long capacity = QuicheNative.connStreamCapacity(connection, id);
            if (capacity == QuicheNative.QuicheError.STREAM_STOPPED.code()
                    || capacity == QuicheNative.QuicheError.INVALID_STREAM_STATE.code()) {
                refixes$stopSending(connection, capacity);
            }
        }
        return sent;
    }

    @Inject(method = "lambda$closeConnection$0", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$trackLocalShutdown(CallbackInfo ci) {
        refixes$localShutdown = true;
    }

    @ModifyReturnValue(method = "isWritable", at = @At("RETURN"), require = 1, allow = 1)
    private boolean refixes$gateOnBacklog(boolean transportWritable) {
        if (!transportWritable || refixes$queueBudget.overflowed()) return false;
        synchronized (refixes$queueLock) {
            return refixes$queuedBytes < refixes$backlogBytes;
        }
    }

    @WrapOperation(
            method = {
                "write(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush(Lcom/hypixel/hytale/protocol/ToClientPacket;)V"
            },
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentLinkedQueue;add(Ljava/lang/Object;)Z"),
            require = 2,
            allow = 2)
    private boolean refixes$accountAdd(
            ConcurrentLinkedQueue<ToClientPacket> queue, Object packet, Operation<Boolean> original) {
        if (closed.get() || refixes$sendDead) return false;
        long bytes = refixes$frameBytes((ToClientPacket) packet);
        boolean paced = SetupAssetPacing.quicheWorker(refixes$queueBudget)
                && bytes >= 0L
                && bytes != Long.MAX_VALUE
                && bytes <= refixes$queueBudget.maxBytes();
        while (true) {
            synchronized (refixes$queueLock) {
                if (closed.get() || refixes$sendDead) return false;
                boolean reserved = paced
                        ? refixes$queuedBytes < refixes$backlogBytes && refixes$queueBudget.tryReserve(bytes, 1)
                        : refixes$reserve(bytes, 1);
                if (reserved) {
                    boolean added = false;
                    try {
                        added = original.call(queue, packet);
                        if (added) {
                            refixes$queuedSizes.enqueue(bytes);
                            refixes$queuedBytes += bytes;
                        }
                        return added;
                    } finally {
                        if (!added) refixes$queueBudget.release(bytes, 1);
                    }
                }
                if (!paced || refixes$queueBudget.overflowed()) return false;
            }
            SetupAssetPacing.awaitQuiche((QuicheChannel) (Object) this, refixes$queueBudget, bytes, 1);
        }
    }

    @WrapOperation(
            method = {
                "writeForClose(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "lambda$disconnect$0(Lcom/hypixel/hytale/protocol/FormattedMessage;)V"
            },
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentLinkedQueue;add(Ljava/lang/Object;)Z"),
            require = 2,
            allow = 2)
    private boolean refixes$accountCloseAdd(
            ConcurrentLinkedQueue<ToClientPacket> queue, Object packet, Operation<Boolean> original) {
        long bytes = refixes$frameBytes((ToClientPacket) packet);
        if (bytes == Long.MAX_VALUE) return false;
        synchronized (refixes$queueLock) {
            if (closed.get() || refixes$sendDead || refixes$queueBudget.overflowed()) return false;
            refixes$queueBudget.reserveUnchecked(bytes, 1);
            boolean added = false;
            try {
                added = original.call(queue, packet);
                if (added) {
                    refixes$queuedSizes.enqueue(bytes);
                    refixes$queuedBytes += bytes;
                }
                return added;
            } finally {
                if (!added) refixes$queueBudget.release(bytes, 1);
            }
        }
    }

    @WrapOperation(
            method = {
                "write([Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush([Lcom/hypixel/hytale/protocol/ToClientPacket;)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/util/concurrent/ConcurrentLinkedQueue;addAll(Ljava/util/Collection;)Z"),
            require = 2,
            allow = 2)
    private boolean refixes$accountAddAll(
            ConcurrentLinkedQueue<ToClientPacket> queue,
            Collection<ToClientPacket> packets,
            Operation<Boolean> original) {
        if (SetupAssetPacing.quicheWorker(refixes$queueBudget)) {
            QuicheChannel channel = (QuicheChannel) (Object) this;
            boolean added = false;
            for (ToClientPacket packet : packets) {
                if (packet == null) continue;
                channel.write(packet);
                if (closed.get() || refixes$sendDead || refixes$queueBudget.overflowed()) return false;
                added = true;
            }
            return added;
        }
        boolean waited = SetupAssetPacing.broadcastWaits(refixes$queueBudget)
                && SetupAssetPacing.awaitQuicheBroadcast((QuicheChannel) (Object) this, refixes$queueBudget);
        boolean lockExempt = !waited && SetupAssetPacing.broadcastExempt();
        boolean uncapped = waited || lockExempt;
        long[] sizes = new long[uncapped ? packets.size() : Math.min(packets.size(), refixes$queueBudget.maxPackets())];
        int index = 0;
        long total = 0L;
        for (ToClientPacket packet : packets) {
            if (packet == null) continue;
            long size = refixes$frameBytes(packet);
            if (index == sizes.length
                    || size == Long.MAX_VALUE
                    || !uncapped && size > refixes$queueBudget.maxBytes() - total) {
                refixes$reserve(Long.MAX_VALUE, 0);
                return false;
            }
            sizes[index++] = size;
            total += size;
        }
        if (index != packets.size())
            packets = packets.stream().filter(packet -> packet != null).toList();
        synchronized (refixes$queueLock) {
            if (closed.get() || refixes$sendDead) return false;
            boolean exempt = lockExempt || waited && !refixes$queueBudget.tryReserve(total, index);
            if (!(exempt ? refixes$queueBudget.reserveExempt(total, index) : waited || refixes$reserve(total, index)))
                return false;
            boolean added = false;
            try {
                added = original.call(queue, packets);
                if (added) {
                    for (int i = 0; i < index; i++) refixes$queuedSizes.enqueue(exempt ? -sizes[i] : sizes[i]);
                    refixes$queuedBytes += total;
                }
                return added;
            } finally {
                if (!added) {
                    if (exempt) refixes$queueBudget.releaseExempt(total, index);
                    else refixes$queueBudget.release(total, index);
                }
            }
        }
    }

    @WrapOperation(
            method = "tryWrite",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/ConcurrentLinkedQueue;poll()Ljava/lang/Object;"),
            require = 1,
            allow = 1)
    private Object refixes$accountPoll(ConcurrentLinkedQueue<ToClientPacket> queue, Operation<Object> original) {
        CompletableFuture<Void> waiter = null;
        Object packet;
        synchronized (refixes$queueLock) {
            if (refixes$queueBudget.overflowed()) return null;
            packet = original.call(queue);
            if (packet != null) {
                long bytes = refixes$queuedSizes.dequeueLong();
                if (bytes < 0L) {
                    refixes$queuedBytes += bytes;
                    refixes$queueBudget.releaseExempt(-bytes, 1);
                } else {
                    refixes$queuedBytes -= bytes;
                    refixes$queueBudget.release(bytes, 1);
                }
            }
            if (refixes$drainWaiter != null && refixes$queuedBytes < refixes$backlogBytes) {
                waiter = refixes$drainWaiter;
                refixes$drainWaiter = null;
            }
        }
        if (waiter != null) waiter.complete(null);
        return packet;
    }

    @Inject(method = "tryWrite", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void refixes$skipWriteAfterClose(CallbackInfoReturnable<Boolean> cir) {
        if (closed.get() || refixes$sendDead || refixes$queueBudget.overflowed()) {
            writable.set(false);
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "firePacketTimeout", at = @At("TAIL"), require = 1, allow = 1)
    private void refixes$flushTimeoutClose(long configuredTimeoutNs, long nowNs, CallbackInfo ci) {
        ((QuicheChannel) (Object) this).flush();
    }

    @ModifyExpressionValue(
            method = "consumePacketData",
            at = @At(value = "INVOKE", target = "Ljava/lang/foreign/MemorySegment;byteSize()J"),
            require = 1,
            allow = 1)
    private long refixes$deferLargePacketBuffer(long capacity) {
        return Long.MAX_VALUE;
    }

    @Inject(
            method = "consumePacketData",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lcom/hypixel/hytale/lib/quiche/QuicheChannel;processingPacket:Z",
                            opcode = Opcodes.PUTFIELD,
                            ordinal = 0,
                            shift = At.Shift.AFTER),
            cancellable = true,
            require = 1,
            allow = 1)
    private void refixes$validateLargePacket(
            MemorySegment packetData, int dataSize, CallbackInfo ci, @Local(index = 3) LocalIntRef offset) {
        if (currentPacketSize <= packetBuffer.byteSize()) return;
        int position = offset.get();
        if (tempPacketBuffer == null) {
            if (currentPacketOffset < PacketIO.FRAME_HEADER_SIZE) {
                int idRead = Math.min(dataSize - position, PacketIO.FRAME_HEADER_SIZE - currentPacketOffset);
                MemorySegment.copy(packetData, position, packetBuffer, currentPacketOffset, idRead);
                position += idRead;
                offset.set(position);
                currentPacketOffset += idRead;
                if (currentPacketOffset < PacketIO.FRAME_HEADER_SIZE) {
                    ci.cancel();
                    return;
                }
            }
            int packetId = packetBuffer.get(PacketIO.PROTO_INT, 0);
            PacketRegistry.PacketInfo info = PacketRegistry.getToServerPacketById(packetId);
            if (info == null) {
                throw new ProtocolException("Unknown packet ID: " + packetId);
            }
            int payloadLength = currentPacketSize - PacketIO.FRAME_HEADER_SIZE;
            if (payloadLength > info.maxSize()) {
                throw new ProtocolException("Packet " + info.name() + " payload size " + payloadLength
                        + " exceeds max size " + info.maxSize());
            }
        }
        MemorySegment current = tempPacketBuffer != null ? tempPacketBuffer : packetBuffer;
        long required =
                (long) currentPacketOffset + Math.min(dataSize - position, currentPacketSize - currentPacketOffset);
        if (required <= current.byteSize()) return;
        long capacity = Math.max(required, current.byteSize() * 2);
        if (capacity * 2 > currentPacketSize) capacity = currentPacketSize;
        if (tempPacketBuffer == null) {
            LOGGER.atWarning().log("Packet size exceeds buffer capacity, allocating new buffer: %d", capacity);
        }
        Arena grownArena = Arena.ofConfined();
        MemorySegment grown;
        try {
            grown = grownArena.allocate(capacity);
            MemorySegment.copy(current, 0, grown, 0, currentPacketOffset);
        } catch (Throwable failure) {
            grownArena.close();
            throw failure;
        }
        Arena previous = tempPacketArena;
        tempPacketArena = grownArena;
        tempPacketBuffer = grown;
        if (previous != null) previous.close();
    }

    @WrapMethod(method = "close()V")
    private void refixes$clearQueueOnClose(Operation<Void> original) {
        try {
            original.call();
        } finally {
            if (closed.get()) refixes$clearQueue();
        }
    }

    @WrapOperation(
            method = "close()V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/protocol/io/ConnectionHandler;closed(Lcom/hypixel/hytale/protocol/NetworkChannel;)V"))
    private void refixes$freeOnClosedFailure(
            ConnectionHandler handler, NetworkChannel channel, Operation<Void> original) {
        try {
            original.call(handler, channel);
        } catch (Throwable failure) {
            try {
                freeTempSendBuffer();
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            try {
                if (tempPacketArena != null) tempPacketArena.close();
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            try {
                arena.close();
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Inject(
            method = {
                "write(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "write([Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush([Lcom/hypixel/hytale/protocol/ToClientPacket;)V"
            },
            at = @At("HEAD"),
            cancellable = true)
    private void refixes$dropWriteAfterClose(CallbackInfo ci) {
        if (closed.get() || refixes$sendDead || refixes$queueBudget.overflowed()) ci.cancel();
    }

    @Inject(
            method = {
                "write(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "write([Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush([Lcom/hypixel/hytale/protocol/ToClientPacket;)V"
            },
            at = @At("RETURN"))
    private void refixes$clearRacedWrite(CallbackInfo ci) {
        if (closed.get() || refixes$sendDead) refixes$clearQueue();
    }
}
