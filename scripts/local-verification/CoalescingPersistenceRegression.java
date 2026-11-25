package cc.irori.refixes.service;

import static local.hyinit.verify.RegressionRunner.require;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoalescingPersistenceRegression {
    private static final List<String> failures = new ArrayList<>();
    private static final Executor THREADS = task -> {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    };

    public static void main(String[] args) {
        run("coalescesToNewestSnapshot", CoalescingPersistenceRegression::coalescesToNewest);
        run("obsoleteFailureKeepsNewestSnapshot", CoalescingPersistenceRegression::obsoleteFailureKeepsNewest);
        run("failedSnapshotRetriedAtFlush", CoalescingPersistenceRegression::retriedAtFlush);
        run("flushFailureSurfaces", CoalescingPersistenceRegression::flushFailureSurfaces);
        run("boundedCloseReturnsNamingUnflushedKeys", CoalescingPersistenceRegression::boundedCloseReturns);
        run("boundedCloseInterruptedKeepsDeadlineAndFlag", CoalescingPersistenceRegression::boundedCloseInterrupted);
        run("concurrentBoundedAndUnboundedClose", CoalescingPersistenceRegression::concurrentClose);
        run("retryExhaustionUnderBoundedClose", CoalescingPersistenceRegression::retryExhaustion);
        run("lateWriteAfterTimeoutStillMutatesButNewestWins", CoalescingPersistenceRegression::lateWriteNewestWins);
        run("reloadStaysUnboundedNoStaleOverwrite", CoalescingPersistenceRegression::reloadNoStaleOverwrite);
        System.out.println(failures.isEmpty() ? "CoalescingPersistenceRegression passed" : "FAILED " + failures);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    interface Case {
        void run() throws Exception;
    }

    private static void run(String name, Case test) {
        try {
            test.run();
        } catch (Throwable error) {
            failures.add(name);
            System.out.println("FAIL " + name + ": " + error);
        }
    }

    private static long ms(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static void close(CoalescingPersistence<?> persistence, long millis) throws IOException {
        persistence.close(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    private static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        void hold() throws IOException {
            entered.countDown();
            try {
                if (!release.await(60, TimeUnit.SECONDS)) throw new IOException("gate timeout");
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
    }

    private static void atomicWrite(Path dir, String key, String value) throws IOException {
        Path temporary = Files.createTempFile(dir, "w-", ".tmp");
        try {
            Files.writeString(temporary, value);
            Files.move(temporary, dir.resolve(key), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static CoalescingPersistence<String> gated(Path dir, Gate gate, String hungValue) {
        return new CoalescingPersistence<>((key, value) -> {
            if (value.equals(hungValue)) gate.hold();
            atomicWrite(dir, key, value);
        });
    }

    private static void coalescesToNewest() throws Exception {
        Gate gate = new Gate();
        List<String> writes = new ArrayList<>();
        CoalescingPersistence<String> persistence = new CoalescingPersistence<>((key, value) -> {
            if (value.equals("first")) gate.hold();
            writes.add(key + ":" + value);
        });
        persistence.submit("world", "first");
        require(gate.entered.await(5, TimeUnit.SECONDS), "writer did not start");
        for (int i = 0; i < 10000; i++) persistence.submit("world", "v" + i);
        persistence.submit("other", "saved");
        gate.release.countDown();
        persistence.close();
        require(writes.equals(List.of("world:first", "world:v9999", "other:saved")), "unbounded or stale writes: " + writes);
        try {
            persistence.submit("world", "late");
            throw new AssertionError("accepted after close");
        } catch (IllegalStateException expected) {
        }
    }

    private static void obsoleteFailureKeepsNewest() throws Exception {
        Gate gate = new Gate();
        List<String> recovered = new ArrayList<>();
        CoalescingPersistence<String> superseded = new CoalescingPersistence<>((key, value) -> {
            if (value.equals("obsolete")) {
                gate.hold();
                throw new IOException("obsolete failure");
            }
            recovered.add(value);
        });
        superseded.submit("world", "obsolete");
        require(gate.entered.await(5, TimeUnit.SECONDS), "failure did not start");
        superseded.submit("world", "newest");
        gate.release.countDown();
        superseded.close();
        require(recovered.equals(List.of("newest")), "obsolete failure superseded newest snapshot");
    }

    private static void retriedAtFlush() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CoalescingPersistence<String> failing = new CoalescingPersistence<>((key, value) -> {
            if (attempts.incrementAndGet() == 1) throw new IOException("expected");
        });
        failing.submit("world", "retry");
        failing.close();
        require(attempts.get() == 2, "failed snapshot was not retried at flush");
    }

    private static void flushFailureSurfaces() throws Exception {
        CoalescingPersistence<String> broken = new CoalescingPersistence<>((key, value) -> {
            throw new IOException("permanent");
        });
        broken.submit("world", "failure");
        try {
            broken.close();
            throw new AssertionError("flush failure hidden");
        } catch (IOException expected) {
        }
    }

    private static void boundedCloseReturns() throws Exception {
        Path dir = Files.createTempDirectory("persist-bounded");
        Gate gate = new Gate();
        CoalescingPersistence<String> persistence = gated(dir, gate, "v1");
        persistence.submit("a", "v1");
        require(gate.entered.await(5, TimeUnit.SECONDS), "writer did not start");
        persistence.submit("b", "v1b");
        long start = System.nanoTime();
        try {
            close(persistence, 300);
            throw new AssertionError("bounded close hid unflushed writes");
        } catch (IOException expected) {
            require(expected.getMessage().contains("[a, b]"), "unflushed keys not named: " + expected.getMessage());
        } finally {
            gate.release.countDown();
        }
        require(ms(start) < 2000, "bounded close exceeded bound");
        try {
            persistence.submit("c", "late");
            throw new AssertionError("accepted after timed out close");
        } catch (IllegalStateException expected) {
        }
    }

    private static void boundedCloseInterrupted() throws Exception {
        Path dir = Files.createTempDirectory("persist-int");
        Gate gate = new Gate();
        CoalescingPersistence<String> persistence = gated(dir, gate, "v1");
        persistence.submit("a", "v1");
        require(gate.entered.await(5, TimeUnit.SECONDS), "writer did not start");
        CompletableFuture<String> outcome = new CompletableFuture<>();
        Thread closer = new Thread(() -> {
            try {
                close(persistence, 800);
                outcome.complete("returned without error");
            } catch (Exception e) {
                outcome.complete("interrupted=" + Thread.currentThread().isInterrupted() + " " + e.getMessage());
            }
        });
        closer.start();
        Thread.sleep(150);
        closer.interrupt();
        String result = outcome.get(5, TimeUnit.SECONDS);
        gate.release.countDown();
        require(result.contains("interrupted=true") && result.contains("[a]"), "interrupt lost or keys hidden: " + result);
    }

    private static void concurrentClose() throws Exception {
        Path dir = Files.createTempDirectory("persist-concurrent");
        Gate gate = new Gate();
        CoalescingPersistence<String> persistence = gated(dir, gate, "v1");
        persistence.submit("a", "v1");
        require(gate.entered.await(5, TimeUnit.SECONDS), "writer did not start");
        CompletableFuture<Void> unbounded = CompletableFuture.runAsync(() -> {
            try {
                persistence.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, THREADS);
        List<CompletableFuture<String>> boundedClosers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            boundedClosers.add(CompletableFuture.supplyAsync(() -> {
                try {
                    close(persistence, 300);
                    return "ok";
                } catch (Exception e) {
                    return e.getMessage();
                }
            }, THREADS));
        }
        for (CompletableFuture<String> closer : boundedClosers) {
            String message = closer.get(5, TimeUnit.SECONDS);
            require(message.contains("Timed out flushing worlds: [a]"), "concurrent bounded close: " + message);
        }
        try {
            unbounded.get(300, TimeUnit.MILLISECONDS);
            throw new AssertionError("unbounded close returned while write hung");
        } catch (TimeoutException expected) {
        }
        gate.release.countDown();
        unbounded.get(5, TimeUnit.SECONDS);
        close(persistence, 100);
        require(Files.readString(dir.resolve("a")).equals("v1"), "write lost");
    }

    private static void retryExhaustion() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CoalescingPersistence<String> persistence = new CoalescingPersistence<>((key, value) -> {
            attempts.incrementAndGet();
            throw new IOException("permanent");
        });
        persistence.submit("a", "v1");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (attempts.get() < 2 && System.nanoTime() < deadline) Thread.sleep(10);
        long start = System.nanoTime();
        try {
            close(persistence, 10_000);
            throw new AssertionError("failure hidden");
        } catch (IOException expected) {
            require(expected.getMessage().startsWith("Failed to flush worlds: [a]"), "wrong failure: " + expected.getMessage());
        }
        require(ms(start) < 5000, "retry exhaustion not finite");
        int settled = attempts.get();
        Thread.sleep(600);
        require(attempts.get() == settled, "retries continued after close");
    }

    private static void lateWriteNewestWins() throws Exception {
        Path dir = Files.createTempDirectory("persist-late");
        Gate gate = new Gate();
        CoalescingPersistence<String> persistence = gated(dir, gate, "v1");
        persistence.submit("a", "v0");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.exists(dir.resolve("a")) && System.nanoTime() < deadline) Thread.sleep(5);
        persistence.submit("a", "v1");
        require(gate.entered.await(5, TimeUnit.SECONDS), "writer did not start");
        persistence.submit("a", "v2");
        try {
            close(persistence, 200);
            throw new AssertionError("timeout hidden");
        } catch (IOException expected) {
        }
        String atReturn = Files.readString(dir.resolve("a"));
        gate.release.countDown();
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!Files.readString(dir.resolve("a")).equals("v2") && System.nanoTime() < deadline) Thread.sleep(5);
        String after = Files.readString(dir.resolve("a"));
        require(atReturn.equals("v0") && after.equals("v2"), "newest snapshot not preserved");
        try (var paths = Files.list(dir)) {
            require(paths.noneMatch(path -> path.toString().endsWith(".tmp")), "temporary file left");
        }
    }

    private static void reloadNoStaleOverwrite() throws Exception {
        Path dir = Files.createTempDirectory("persist-reload");
        Gate gate = new Gate();
        CoalescingPersistence<String> old = gated(dir, gate, "old");
        old.submit("world", "old");
        require(gate.entered.await(5, TimeUnit.SECONDS), "writer did not start");
        CompletableFuture<Void> unload = CompletableFuture.runAsync(() -> {
            try {
                old.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, THREADS);
        try {
            unload.get(500, TimeUnit.MILLISECONDS);
            throw new AssertionError("reload close returned while write hung");
        } catch (TimeoutException expected) {
        }
        gate.release.countDown();
        unload.get(5, TimeUnit.SECONDS);
        CoalescingPersistence<String> reopened = gated(dir, new Gate(), "never");
        reopened.submit("world", "new");
        reopened.close();
        require(Files.readString(dir.resolve("world")).equals("new"), "new generation lost");
    }
}
