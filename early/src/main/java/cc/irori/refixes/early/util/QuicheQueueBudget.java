package cc.irori.refixes.early.util;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.OutgoingQueueLimits;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class QuicheQueueBudget {

    private static final CompletableFuture<Void> DRAINED = CompletableFuture.completedFuture(null);

    private final OutgoingQueueLimits limits = EarlyOptions.outgoingQueueLimits();
    private final AtomicLong sentBytes = new AtomicLong();

    private long bytes;
    private int packets;
    private long exemptBytes;
    private int exemptPackets;
    private long releasedPackets;
    private volatile boolean overflowed;
    private CompletableFuture<Void> waiter;
    private long waitingBytes;
    private int waitingPackets;

    public long maxBytes() {
        return limits.maxBytes();
    }

    public int maxPackets() {
        return limits.maxPackets();
    }

    public boolean reserve(long addedBytes, int addedPackets) {
        CompletableFuture<Void> ready;
        synchronized (this) {
            if (tryReserve(addedBytes, addedPackets)) return true;
            overflowed = true;
            ready = waiter;
            waiter = null;
        }
        if (ready != null) ready.complete(null);
        return false;
    }

    public synchronized boolean tryReserve(long addedBytes, int addedPackets) {
        if (overflowed || !fits(addedBytes, addedPackets)) return false;
        bytes += addedBytes;
        packets += addedPackets;
        return true;
    }

    public synchronized void reserveUnchecked(long addedBytes, int addedPackets) {
        bytes += addedBytes;
        packets += addedPackets;
    }

    public synchronized boolean reserveExempt(long addedBytes, int addedPackets) {
        if (overflowed) return false;
        bytes += addedBytes;
        packets += addedPackets;
        exemptBytes += addedBytes;
        exemptPackets += addedPackets;
        return true;
    }

    public synchronized CompletableFuture<Void> drained(long addedBytes, int addedPackets) {
        if (overflowed || fits(addedBytes, addedPackets)) return DRAINED;
        if (waiter == null) {
            waiter = new CompletableFuture<>();
            waitingBytes = addedBytes;
            waitingPackets = addedPackets;
        } else {
            waitingBytes = Math.max(waitingBytes, addedBytes);
            waitingPackets = Math.max(waitingPackets, addedPackets);
        }
        return waiter;
    }

    private boolean fits(long addedBytes, int addedPackets) {
        return addedBytes >= 0L
                && addedBytes != Long.MAX_VALUE
                && addedPackets >= 0
                && addedBytes <= limits.maxBytes() - (bytes - exemptBytes)
                && addedPackets <= limits.maxPackets() - (packets - exemptPackets);
    }

    public void release(long removedBytes, int removedPackets) {
        release(removedBytes, removedPackets, false);
    }

    public void releaseExempt(long removedBytes, int removedPackets) {
        release(removedBytes, removedPackets, true);
    }

    private void release(long removedBytes, int removedPackets, boolean exempt) {
        CompletableFuture<Void> ready = null;
        synchronized (this) {
            bytes -= removedBytes;
            releasedPackets += removedPackets;
            packets -= removedPackets;
            if (exempt) {
                exemptBytes -= removedBytes;
                exemptPackets -= removedPackets;
            }
            if (waiter != null && (overflowed || fits(waitingBytes, waitingPackets))) {
                ready = waiter;
                waiter = null;
            }
        }
        if (ready != null) ready.complete(null);
    }

    public void sent(long bytes) {
        sentBytes.addAndGet(bytes);
    }

    public long progress() {
        long released;
        synchronized (this) {
            released = releasedPackets;
        }
        return released + sentBytes.get();
    }

    public synchronized long pendingBytes() {
        return bytes;
    }

    public synchronized int pendingPackets() {
        return packets;
    }

    public synchronized long cappedBytes() {
        return bytes - exemptBytes;
    }

    public synchronized int cappedPackets() {
        return packets - exemptPackets;
    }

    public synchronized long exemptBytes() {
        return exemptBytes;
    }

    public synchronized int exemptPackets() {
        return exemptPackets;
    }

    public boolean overflowed() {
        return overflowed;
    }
}
