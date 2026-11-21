package local.hyinit.verify;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.mixin.MixinStoreShutdown;
import cc.irori.refixes.early.mixin.MixinWorldShutdown;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.ShutdownReason;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
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
        requireWaits("runtime world removal", StoreShutdownRegression::await);
        serverShuttingDown(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                try {
                    await(new CompletableFuture<>());
                } catch (ClassCastException fixtureIsNotAStore) {
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
        requireWaits("world config save outside server shutdown", StoreShutdownRegression::awaitWorldConfig);
        serverShuttingDown(true);
        EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.setSupplier(() -> 0);
        requireWaits("world config save with a timeout of 0", StoreShutdownRegression::awaitWorldConfig);
        EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.setSupplier(() -> 1);
        long started = System.nanoTime();
        try {
            awaitWorldConfig(new CompletableFuture<>());
        } catch (ClassCastException fixtureIsNotAWorld) {
        }
        long waited = System.nanoTime() - started;
        require(waited >= TimeUnit.MILLISECONDS.toNanos(900) && waited < TimeUnit.SECONDS.toNanos(3),
                "world config save wait was not limited to the timeout: " + TimeUnit.NANOSECONDS.toMillis(waited) + " ms");
    }

    private interface Wait {
        Object on(CompletableFuture<?> future) throws Exception;
    }

    private static void requireWaits(String label, Wait wait) throws Exception {
        CompletableFuture<Object> save = new CompletableFuture<>();
        AtomicReference<Object> result = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                result.set(wait.on(save));
            } catch (Throwable error) {
                result.set(error);
            }
        });
        thread.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (thread.isAlive() && thread.getState() != Thread.State.WAITING
                && thread.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.onSpinWait();
        require(thread.getState() == Thread.State.WAITING, label + " did not wait for the save without a limit");
        save.complete("saved");
        thread.join(2000);
        require("saved".equals(result.get()), label + " lost the save result: " + result.get());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
