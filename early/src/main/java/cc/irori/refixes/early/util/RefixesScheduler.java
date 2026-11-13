package cc.irori.refixes.early.util;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public final class RefixesScheduler {
    private static ScheduledThreadPoolExecutor executor;

    private RefixesScheduler() {}

    public static synchronized ScheduledExecutorService get() {
        if (executor == null) {
            executor = new ScheduledThreadPoolExecutor(1, task -> {
                Thread thread = new Thread(task, "Refixes-Scheduler");
                thread.setDaemon(true);
                return thread;
            });
            executor.setRemoveOnCancelPolicy(true);
            executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(true);
        }
        return executor;
    }

    public static synchronized void shutdown() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }
}
