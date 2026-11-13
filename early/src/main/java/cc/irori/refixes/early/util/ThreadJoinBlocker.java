package cc.irori.refixes.early.util;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

public final class ThreadJoinBlocker implements ForkJoinPool.ManagedBlocker {
    private final Thread thread;
    private final long millis;

    private ThreadJoinBlocker(Thread thread, long millis) {
        this.thread = thread;
        this.millis = millis;
    }

    public static void join(Thread thread, long millis) throws InterruptedException {
        ThreadJoinBlocker blocker = new ThreadJoinBlocker(thread, millis);
        try {
            ForkJoinPool.managedBlock(blocker);
        } catch (RejectedExecutionException saturated) {
            thread.join(millis);
        }
    }

    @Override
    public boolean block() throws InterruptedException {
        thread.join(millis);
        return true;
    }

    @Override
    public boolean isReleasable() {
        return !thread.isAlive();
    }
}
