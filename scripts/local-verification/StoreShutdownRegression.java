package local.hyinit.verify;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.mixin.MixinStoreShutdown;
import cc.irori.refixes.early.mixin.MixinWorldShutdown;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.ShutdownReason;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class StoreShutdownRegression {
    private static Object await(CompletableFuture<?> future) throws Exception {
        return invoke(MixinStoreShutdown.class, new MixinStoreShutdown(), "refixes$joinWithTimeout", future);
    }

    private static Object awaitWorldConfig(CompletableFuture<?> future) throws Exception {
        return invoke(MixinWorldShutdown.class, new MixinWorldShutdown(), "refixes$timeoutConfigSave", future);
    }

    private static Object invoke(Class<?> type, Object mixin, String name, CompletableFuture<?> future) throws Exception {
        Method method = type.getDeclaredMethod(name, CompletableFuture.class);
        method.setAccessible(true);
        try {
            return method.invoke(mixin, future);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception exception) throw exception;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        }
    }

    private static void serverShuttingDown(boolean shuttingDown) throws Exception {
        Field instance = HytaleServer.class.getDeclaredField("instance");
        instance.setAccessible(true);
        if (instance.get(null) == null) {
            Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Object server = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, HytaleServer.class);
            Field shutdown = HytaleServer.class.getDeclaredField("shutdown");
            shutdown.setAccessible(true);
            shutdown.set(server, new AtomicReference<ShutdownReason>());
            instance.set(null, server);
        }
        Field shutdown = HytaleServer.class.getDeclaredField("shutdown");
        shutdown.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<ShutdownReason> reason = (AtomicReference<ShutdownReason>) shutdown.get(HytaleServer.get());
        reason.set(shuttingDown ? ShutdownReason.SHUTDOWN : null);
    }

    public static void main(String[] args) throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[0]);
        serverShuttingDown(false);
        EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.setSupplier(() -> 1);
        CompletableFuture<Object> runtimeSave = new CompletableFuture<>();
        CompletableFuture<Object> runtimeResult = CompletableFuture.supplyAsync(() -> {
            try {
                return await(runtimeSave);
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        });
        Thread.sleep(1500);
        require(!runtimeResult.isDone(), "runtime world removal abandoned a pending save");
        runtimeSave.complete("saved");
        require("saved".equals(runtimeResult.get(2, TimeUnit.SECONDS)), "runtime save result lost");
        serverShuttingDown(true);
        for (int timeout : new int[] {0, 1}) {
            EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.setSupplier(() -> timeout);
            require("saved".equals(await(CompletableFuture.completedFuture("saved"))), "completed save lost its result");
            IOException cause = new IOException("disk write failed");
            try {
                await(CompletableFuture.failedFuture(cause));
                throw new AssertionError("failed save was swallowed with timeout=" + timeout);
            } catch (CompletionException expected) {
                require(expected.getCause() == cause, "save failure lost its cause");
            }
            CompletableFuture<Object> cancelled = new CompletableFuture<>();
            cancelled.cancel(false);
            try {
                await(cancelled);
                throw new AssertionError("cancelled save was swallowed with timeout=" + timeout);
            } catch (CancellationException expected) {
            }
        }
        EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.setSupplier(() -> 1);
        Thread.currentThread().interrupt();
        require("saved".equals(await(CompletableFuture.completedFuture("saved"))), "pre-interrupted completed save failed");
        require(Thread.interrupted(), "existing interrupt was lost");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                try {
                    await(new CompletableFuture<>());
                    throw new AssertionError("interrupted pending save was not handed to world retention");
                } catch (ClassCastException retention) {
                    require(retention.getStackTrace()[0].getMethodName().equals("refixes$joinWithTimeout"),
                            "unexpected cast failure: " + retention);
                }
                require(Thread.currentThread().isInterrupted(), "interrupt received during save wait was lost");
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        thread.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (thread.isAlive() && thread.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.onSpinWait();
        thread.interrupt();
        thread.join(2500);
        require(!thread.isAlive(), "interrupted save wait did not terminate");
        if (failure.get() != null) throw new AssertionError(failure.get());
        worldConfigSave();
        System.out.println("StoreShutdownRegression PASS");
    }

    private static void worldConfigSave() throws Exception {
        serverShuttingDown(false);
        EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.setSupplier(() -> 1);
        requireWaits("world config save outside server shutdown");
        serverShuttingDown(true);
        EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.setSupplier(() -> 0);
        requireWaits("world config save with a timeout of 0");
        EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.setSupplier(() -> 1);
        require("saved".equals(awaitWorldConfig(CompletableFuture.completedFuture("saved"))), "completed world config save lost its result");
        IOException cause = new IOException("disk write failed");
        try {
            awaitWorldConfig(CompletableFuture.failedFuture(cause));
            throw new AssertionError("failed world config save was swallowed");
        } catch (CompletionException expected) {
            require(expected.getCause() == cause, "world config save failure lost its cause");
        }
        long started = System.nanoTime();
        try {
            awaitWorldConfig(new CompletableFuture<>());
            throw new AssertionError("pending world config save was not handed to world retention");
        } catch (ClassCastException retention) {
            require(retention.getStackTrace()[0].getMethodName().equals("refixes$timeoutConfigSave"),
                    "unexpected cast failure: " + retention);
        }
        long waited = System.nanoTime() - started;
        require(waited >= TimeUnit.MILLISECONDS.toNanos(900) && waited < TimeUnit.SECONDS.toNanos(3),
                "world config save wait was not limited to the timeout: " + TimeUnit.NANOSECONDS.toMillis(waited) + " ms");
    }

    private static void requireWaits(String label) throws Exception {
        CompletableFuture<Object> save = new CompletableFuture<>();
        CompletableFuture<Object> result = CompletableFuture.supplyAsync(() -> {
            try {
                return awaitWorldConfig(save);
            } catch (Exception error) {
                throw new CompletionException(error);
            }
        });
        Thread.sleep(1500);
        require(!result.isDone(), label + " stopped waiting");
        save.complete("saved");
        require("saved".equals(result.get(2, TimeUnit.SECONDS)), label + " lost its result");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
