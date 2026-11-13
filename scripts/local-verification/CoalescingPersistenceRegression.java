package cc.irori.refixes.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class CoalescingPersistenceRegression {
    public static void main(String[] args) throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> writes = new ArrayList<>();
        CoalescingPersistence<String> persistence = new CoalescingPersistence<>((key, value) -> {
            if (value.equals("first")) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("timeout");
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
            writes.add(key + ":" + value);
        });
        persistence.submit("world", "first");
        if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("writer did not start");
        for (int i = 0; i < 10000; i++) persistence.submit("world", "v" + i);
        persistence.submit("other", "saved");
        release.countDown();
        persistence.close();
        if (!writes.equals(List.of("world:first", "world:v9999", "other:saved"))) {
            throw new AssertionError("unbounded or stale writes: " + writes);
        }
        CountDownLatch failingEntered = new CountDownLatch(1);
        CountDownLatch failingRelease = new CountDownLatch(1);
        List<String> recovered = new ArrayList<>();
        CoalescingPersistence<String> superseded = new CoalescingPersistence<>((key, value) -> {
            if (value.equals("obsolete")) {
                failingEntered.countDown();
                try {
                    if (!failingRelease.await(5, TimeUnit.SECONDS)) throw new IOException("timeout");
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
                throw new IOException("obsolete failure");
            }
            recovered.add(value);
        });
        superseded.submit("world", "obsolete");
        if (!failingEntered.await(5, TimeUnit.SECONDS)) throw new AssertionError("failure did not start");
        superseded.submit("world", "newest");
        failingRelease.countDown();
        superseded.close();
        if (!recovered.equals(List.of("newest"))) throw new AssertionError("obsolete failure superseded newest snapshot");
        AtomicInteger attempts = new AtomicInteger();
        CoalescingPersistence<String> failing = new CoalescingPersistence<>((key, value) -> {
            if (attempts.incrementAndGet() == 1) throw new IOException("expected");
        });
        failing.submit("world", "retry");
        failing.close();
        if (attempts.get() != 2) throw new AssertionError("failed snapshot was not retried at flush");
        CoalescingPersistence<String> broken = new CoalescingPersistence<>((key, value) -> {
            throw new IOException("permanent");
        });
        broken.submit("world", "failure");
        try {
            broken.close();
            throw new AssertionError("flush failure hidden");
        } catch (IOException expected) {
        }
        try {
            persistence.submit("world", "late");
            throw new AssertionError("accepted after close");
        } catch (IllegalStateException expected) {
        }
        System.out.println("CoalescingPersistenceRegression passed");
    }
}
