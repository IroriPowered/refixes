package cc.irori.refixes.service;

import com.hypixel.hytale.server.core.universe.world.World;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class PersistenceShutdownRegression {
    private static final List<String> failures = new ArrayList<>();
    private static final Method BOUNDED = bounded();
    private static final java.util.concurrent.Executor THREADS = task -> {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    };

    public static void main(String[] args) throws Exception {
        System.out.println("source=" + CoalescingPersistence.class.getProtectionDomain().getCodeSource().getLocation());
        System.out.println("boundedClose=" + (BOUNDED != null));
        boolean service = args.length > 0 && args[0].equals("service");
        if (service) {
            run("serviceServerStopBounded", PersistenceShutdownRegression::serviceServerStopBounded);
        } else {
            run("hungWriterBlocksUnboundedCloseEvenWhenInterrupted", PersistenceShutdownRegression::hungWriterBlocksUnboundedClose);
            run("boundedCloseReturnsNamingUnflushedKeys", PersistenceShutdownRegression::boundedCloseReturns);
            run("boundedCloseInterruptedKeepsDeadlineAndFlag", PersistenceShutdownRegression::boundedCloseInterrupted);
            run("concurrentBoundedAndUnboundedClose", PersistenceShutdownRegression::concurrentClose);
            run("retryExhaustionUnderBoundedClose", PersistenceShutdownRegression::retryExhaustion);
            run("lateWriteAfterTimeoutStillMutatesButNewestWins", PersistenceShutdownRegression::lateWriteNewestWins);
            run("reloadStaysUnboundedNoStaleOverwrite", PersistenceShutdownRegression::reloadNoStaleOverwrite);
        }
        System.out.println(failures.isEmpty() ? "ALL PASS" : "FAILED " + failures);
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    interface Case {
        void run() throws Exception;
    }

    private static void run(String name, Case test) {
        long start = System.nanoTime();
        try {
            test.run();
            System.out.println("PASS " + name + " " + ms(start) + "ms");
        } catch (Throwable error) {
            failures.add(name);
            System.out.println("FAIL " + name + " " + ms(start) + "ms: " + error);
        }
    }

    private static long ms(long start) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static Method bounded() {
        try {
            Method method = CoalescingPersistence.class.getDeclaredMethod("close", long.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static void close(CoalescingPersistence<?> persistence, long millis) throws Exception {
        if (BOUNDED == null) throw new UnsupportedOperationException("no bounded close at this revision");
        try {
            BOUNDED.invoke(persistence, TimeUnit.MILLISECONDS.toNanos(millis));
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof Exception exception) throw exception;
            throw e;
        }
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

    private static void hungWriterBlocksUnboundedClose() throws Exception {
        Path dir = Files.createTempDirectory("persist-hung");
        Gate gate = new Gate();
        CoalescingPersistence<String> persistence = gated(dir, gate, "v1");
        persistence.submit("a", "v1");
        require(gate.entered.await(5, TimeUnit.SECONDS), "writer did not start");
        Thread closer = new Thread(() -> {
            try {
                persistence.close();
            } catch (IOException ignored) {
            }
        });
        closer.start();
        closer.join(1000);
        require(closer.isAlive(), "unbounded close returned while write hung");
        closer.interrupt();
        closer.join(1000);
        boolean stillBlocked = closer.isAlive();
        System.out.println("  unbounded close blocked after 1s and after interrupt+1s: " + stillBlocked);
        gate.release.countDown();
        closer.join(5000);
        require(stillBlocked, "interrupt released unbounded close while write hung");
        require(!closer.isAlive(), "close did not finish after release");
        require(Files.readString(dir.resolve("a")).equals("v1"), "released write lost");
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
            System.out.println("  bounded close returned after " + ms(start) + "ms: " + expected.getMessage());
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
            long start = System.nanoTime();
            try {
                close(persistence, 800);
                outcome.complete("returned without error");
            } catch (Exception e) {
                outcome.complete("threw after " + ms(start) + "ms interrupted=" + Thread.currentThread().isInterrupted() + " " + e.getMessage());
            }
        });
        closer.start();
        Thread.sleep(150);
        closer.interrupt();
        String result = outcome.get(5, TimeUnit.SECONDS);
        gate.release.countDown();
        System.out.println("  " + result);
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
            System.out.println("  exhausted after " + attempts.get() + " attempts in " + ms(start) + "ms: " + expected.getMessage());
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
        System.out.println("  file at bounded return=" + atReturn + " after release=" + after + " (worker kept writing after close returned)");
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

    @SuppressWarnings("unchecked")
    private static void serviceServerStopBounded() throws Exception {
        Path dir = Files.createTempDirectory("persist-service");
        ChunkLoaderService service = new ChunkLoaderService(dir);
        Field field = ChunkLoaderService.class.getDeclaredField("persistence");
        field.setAccessible(true);
        Method write = ChunkLoaderService.class.getDeclaredMethod("writeSnapshot", String.class, List.class);
        write.setAccessible(true);
        Gate gate = new Gate();
        List<String> written = new CopyOnWriteArrayList<>();
        CoalescingPersistence<List<?>> replacement = new CoalescingPersistence<>((key, snapshot) -> {
            gate.hold();
            try {
                write.invoke(service, key, snapshot);
            } catch (ReflectiveOperationException e) {
                throw new IOException(e);
            }
            written.add(key);
        });
        ((CoalescingPersistence<?>) field.get(service)).close();
        field.set(service, replacement);
        World world = new World("stopping");
        service.addChunk(world, 1, 2, "kept", 0);
        require(gate.entered.await(5, TimeUnit.SECONDS), "real writer did not start");
        Method shutdown;
        Object[] arguments;
        try {
            shutdown = ChunkLoaderService.class.getMethod("shutdown", boolean.class);
            arguments = new Object[] {true};
        } catch (NoSuchMethodException e) {
            shutdown = ChunkLoaderService.class.getMethod("shutdown");
            arguments = new Object[0];
        }
        Method call = shutdown;
        Object[] callArguments = arguments;
        long start = System.nanoTime();
        Thread stopper = new Thread(() -> {
            try {
                call.invoke(service, callArguments);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }, "ShutdownThread");
        stopper.start();
        stopper.join(TimeUnit.SECONDS.toMillis(40));
        boolean returned = !stopper.isAlive();
        System.out.println("  server-stop shutdown(" + arguments.length + " args) returned=" + returned + " after " + ms(start) + "ms; file exists=" + Files.exists(dir.resolve("chunkloaders/stopping.json")));
        gate.release.countDown();
        stopper.join(5000);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (written.isEmpty() && System.nanoTime() < deadline) Thread.sleep(5);
        System.out.println("  after release written=" + written + " file=" + Files.exists(dir.resolve("chunkloaders/stopping.json")));
        require(returned, "server-stop shutdown blocked on hung writer for 40s");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
