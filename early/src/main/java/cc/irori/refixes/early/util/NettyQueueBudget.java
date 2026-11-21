package cc.irori.refixes.early.util;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.util.AttributeKey;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class NettyQueueBudget {

    public static final AttributeKey<NettyQueueBudget> ATTRIBUTE = AttributeKey.valueOf("refixes.quic.queueBudget");

    private static final long SOFT_BYTES = 512L * 1024;
    private static final int SOFT_PACKETS = 1024;
    private static final CompletableFuture<Void> DRAINED = CompletableFuture.completedFuture(null);

    private final Channel owner;
    private final QuicheQueueBudget pending = new QuicheQueueBudget();
    private final long softBytes;
    private final int softPackets;
    private final List<Channel> channels = new ArrayList<>();
    private CompletableFuture<Void> waiter;
    private long waitingBytes;
    private int waitingPackets;
    private Throwable failure;
    private boolean closeRequested;

    public NettyQueueBudget(Channel owner) {
        this.owner = owner;
        softBytes = Math.min(SOFT_BYTES, pending.maxBytes());
        softPackets = Math.min(SOFT_PACKETS, pending.maxPackets());
    }

    public void sent(long bytes) {
        pending.sent(bytes);
    }

    public long progress() {
        return pending.progress();
    }

    public long maxBytes() {
        return pending.maxBytes();
    }

    public int maxPackets() {
        return pending.maxPackets();
    }

    public boolean owns(Channel channel) {
        return (channel.parent() instanceof QuicChannel ? channel.parent() : channel) == owner;
    }

    public void register(Channel channel) {
        synchronized (this) {
            if (channels.contains(channel)) return;
            channels.add(channel);
        }
        channel.closeFuture().addListener(ignored -> closed(channel));
    }

    public boolean reserve(long bytes, int packets) {
        CompletableFuture<Void> ready;
        synchronized (this) {
            if (failure != null) return false;
            long pendingBytes = pending.pendingBytes();
            int pendingPackets = pending.pendingPackets();
            if (pending.reserve(bytes, packets)) return true;
            boolean byteLimit = bytes == Long.MAX_VALUE || bytes > pending.maxBytes() - pending.cappedBytes();
            boolean packetLimit = packets > pending.maxPackets() - pending.cappedPackets();
            failure = new IllegalStateException("Outgoing packet queue "
                    + (byteLimit && packetLimit ? "byte and packet" : byteLimit ? "byte" : "packet")
                    + " limit exceeded: pendingBytes=" + pendingBytes + ", pendingPackets=" + pendingPackets
                    + ", requestedBytes=" + bytes + ", requestedPackets=" + packets
                    + ", maxBytes=" + pending.maxBytes() + ", maxPackets=" + pending.maxPackets());
            ready = waiter;
            waiter = null;
        }
        if (ready != null) ready.complete(null);
        return false;
    }

    public synchronized boolean tryReserve(long bytes, int packets) {
        if (bytes < 0L
                || bytes == Long.MAX_VALUE
                || bytes > pending.maxBytes()
                || packets < 0
                || packets > pending.maxPackets()
                || failure != null
                || backlogged(bytes, packets)) return false;
        return pending.reserve(bytes, packets);
    }

    public synchronized boolean reserveExempt(long bytes, int packets) {
        return failure == null && pending.reserveExempt(bytes, packets);
    }

    public synchronized boolean tryReserveWithinLimits(long bytes, int packets) {
        return failure == null && pending.tryReserve(bytes, packets);
    }

    public void release(long bytes, int packets, Throwable writeFailure) {
        release(bytes, packets, writeFailure, false);
    }

    public void releaseExempt(long bytes, int packets, Throwable writeFailure) {
        release(bytes, packets, writeFailure, true);
    }

    private void release(long bytes, int packets, Throwable writeFailure, boolean exempt) {
        CompletableFuture<Void> ready = null;
        synchronized (this) {
            if (exempt) pending.releaseExempt(bytes, packets);
            else pending.release(bytes, packets);
            if (waiter != null && (writeFailure != null || !waiting())) {
                ready = waiter;
                waiter = null;
            }
        }
        if (ready != null) ready.complete(null);
    }

    public boolean backlogged() {
        return backlogged(0L, 0);
    }

    public synchronized boolean backlogged(long bytes, int packets) {
        return failure == null
                && (pending.pendingBytes() >= softBytes
                        || pending.pendingPackets() >= softPackets
                        || bytes != Long.MAX_VALUE
                                && bytes <= pending.maxBytes()
                                && bytes > pending.maxBytes() - pending.cappedBytes()
                        || packets <= pending.maxPackets() && packets > pending.maxPackets() - pending.cappedPackets());
    }

    public CompletableFuture<Void> drained() {
        return drained(0L, 0);
    }

    public synchronized CompletableFuture<Void> drained(long bytes, int packets) {
        if (!backlogged(bytes, packets)) return DRAINED;
        if (waiter == null) {
            waiter = new CompletableFuture<>();
            waitingBytes = bytes;
            waitingPackets = packets;
        } else {
            waitingBytes = Math.max(waitingBytes, bytes);
            waitingPackets = Math.max(waitingPackets, packets);
        }
        return waiter;
    }

    private boolean waiting() {
        return backlogged(waitingBytes, waitingPackets);
    }

    public void flush() {
        Channel[] streams;
        synchronized (this) {
            streams = channels.toArray(Channel[]::new);
        }
        for (Channel stream : streams) {
            if (stream != owner && stream.isActive()) stream.flush();
        }
        if (owner.isActive()) owner.flush();
    }

    public synchronized Throwable failure() {
        return failure;
    }

    public boolean fail(Throwable cause) {
        CompletableFuture<Void> ready;
        synchronized (this) {
            if (failure != null) return false;
            failure = cause;
            ready = waiter;
            waiter = null;
        }
        if (ready != null) ready.complete(null);
        return true;
    }

    public synchronized boolean requestClose() {
        if (closeRequested) return false;
        closeRequested = true;
        return true;
    }

    private void closed(Channel channel) {
        CompletableFuture<Void> ready;
        synchronized (this) {
            channels.remove(channel);
            if (channel == owner) channels.clear();
            if (channel == owner && failure == null) failure = new ClosedChannelException();
            ready = waiter;
            waiter = null;
        }
        if (ready != null) ready.complete(null);
    }

    public static final class Batch {

        private final ChannelPromise promise;
        private int remaining = 1;
        private Throwable failure;

        public Batch(ChannelPromise promise) {
            this.promise = promise;
        }

        public synchronized void add() {
            remaining++;
        }

        public void finish() {
            complete(null);
        }

        public void complete(Throwable cause) {
            Throwable failed;
            synchronized (this) {
                if (failure == null) failure = cause;
                if (--remaining != 0) return;
                failed = failure;
            }
            if (failed == null) promise.trySuccess();
            else promise.tryFailure(failed);
        }
    }
}
