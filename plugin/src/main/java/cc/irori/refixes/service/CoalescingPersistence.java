package cc.irori.refixes.service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

final class CoalescingPersistence<T> implements AutoCloseable {
    interface Writer<T> {
        void write(String key, T snapshot) throws IOException;
    }

    private final Writer<T> writer;
    private final Map<String, T> pending = new LinkedHashMap<>();
    private final Map<String, T> failed = new LinkedHashMap<>();
    private final Thread worker;
    private boolean closing;
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
        boolean retrying = false;
        while (true) {
            String key;
            T snapshot;
            synchronized (this) {
                while (pending.isEmpty() && !closing) {
                    try {
                        wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                if (pending.isEmpty()) {
                    if (retrying || failed.isEmpty()) return;
                    pending.putAll(failed);
                    failed.clear();
                    retrying = true;
                }
                var entry = pending.entrySet().iterator().next();
                key = entry.getKey();
                snapshot = entry.getValue();
                pending.remove(key);
            }
            try {
                writer.write(key, snapshot);
                synchronized (this) {
                    failed.remove(key);
                }
            } catch (Exception e) {
                synchronized (this) {
                    if (!pending.containsKey(key)) failed.put(key, snapshot);
                    failure = e instanceof IOException io ? io : new IOException(e);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            closing = true;
            notifyAll();
        }
        boolean interrupted = false;
        while (worker.isAlive()) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        synchronized (this) {
            if (!failed.isEmpty()) throw new IOException("Failed to flush worlds: " + failed.keySet(), failure);
        }
    }
}
