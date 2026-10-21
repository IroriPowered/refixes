package cc.irori.refixes.service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

final class CoalescingPersistence<T> implements AutoCloseable {
    interface Writer<T> {
        void write(String key, T snapshot) throws IOException;
    }

    private static final long RETRY_DELAY_NANOS = TimeUnit.MILLISECONDS.toNanos(250);
    private static final long MAX_RETRY_DELAY_NANOS = TimeUnit.SECONDS.toNanos(30);
    private static final int CLOSE_ATTEMPTS = 3;

    private static final class Retry<T> {
        final T snapshot;
        int failures;
        long due;

        Retry(T snapshot) {
            this.snapshot = snapshot;
        }
    }

    private final Writer<T> writer;
    private final Map<String, T> pending = new LinkedHashMap<>();
    private final Map<String, Retry<T>> failed = new LinkedHashMap<>();
    private final Thread worker;
    private boolean closing;
    private String writing;
    private IOException failure;

    CoalescingPersistence(Writer<T> writer) {
        this.writer = writer;
        worker = new Thread(this::run, "Refixes-chunkloader-persistence");
        worker.setDaemon(true);
        worker.start();
    }

    synchronized void submit(String key, T snapshot) {
        if (closing) throw new IllegalStateException("Persistence is closed");
        failed.remove(key);
        pending.put(key, snapshot);
        notifyAll();
    }

    private void run() {
        while (true) {
            String key;
            T snapshot;
            Retry<T> retry = null;
            synchronized (this) {
                while (true) {
                    String dueKey = null;
                    Retry<T> next = null;
                    for (var entry : failed.entrySet()) {
                        Retry<T> candidate = entry.getValue();
                        if (closing && candidate.failures >= CLOSE_ATTEMPTS) continue;
                        if (next == null || candidate.due - next.due < 0) {
                            dueKey = entry.getKey();
                            next = candidate;
                        }
                    }
                    long delay = next == null ? 0 : next.due - System.nanoTime();
                    if (next != null && delay <= 0) {
                        key = dueKey;
                        snapshot = next.snapshot;
                        retry = next;
                        break;
                    }
                    if (!pending.isEmpty()) {
                        var entry = pending.entrySet().iterator().next();
                        key = entry.getKey();
                        snapshot = entry.getValue();
                        pending.remove(key);
                        break;
                    }
                    if (next == null && closing) return;
                    try {
                        if (next == null) wait();
                        else TimeUnit.NANOSECONDS.timedWait(this, delay);
                    } catch (InterruptedException ignored) {
                    }
                }
                writing = key;
            }
            try {
                writer.write(key, snapshot);
                synchronized (this) {
                    writing = null;
                    failed.remove(key);
                }
            } catch (Exception e) {
                synchronized (this) {
                    writing = null;
                    failure = e instanceof IOException io ? io : new IOException(e);
                    if (pending.containsKey(key) || (retry != null && failed.get(key) != retry)) continue;
                    if (retry == null) {
                        retry = new Retry<>(snapshot);
                        failed.put(key, retry);
                    }
                    retry.failures++;
                    long delay = RETRY_DELAY_NANOS << Math.min(retry.failures - 1, 7);
                    retry.due = System.nanoTime() + Math.min(delay, MAX_RETRY_DELAY_NANOS);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        close(Long.MAX_VALUE);
    }

    void close(long timeoutNanos) throws IOException {
        synchronized (this) {
            if (!closing) {
                closing = true;
                long now = System.nanoTime();
                for (Retry<T> retry : failed.values()) {
                    retry.failures = 0;
                    retry.due = now;
                }
            }
            notifyAll();
        }
        long deadline = System.nanoTime() + timeoutNanos;
        boolean interrupted = false;
        while (worker.isAlive()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) break;
            try {
                TimeUnit.NANOSECONDS.timedJoin(worker, remaining);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        synchronized (this) {
            if (worker.isAlive()) {
                Set<String> unflushed = new LinkedHashSet<>();
                if (writing != null) unflushed.add(writing);
                unflushed.addAll(pending.keySet());
                unflushed.addAll(failed.keySet());
                throw new IOException("Timed out flushing worlds: " + unflushed, failure);
            }
            if (!failed.isEmpty()) throw new IOException("Failed to flush worlds: " + failed.keySet(), failure);
        }
    }
}
