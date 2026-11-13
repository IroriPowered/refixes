package cc.irori.refixes.early.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class WorldPolicySaveJoin {
    private WorldPolicySaveJoin() {}

    public static Object await(CompletableFuture<?> future, long timeout, TimeUnit unit, String operation) {
        boolean interrupted = Thread.interrupted();
        try {
            return future.get(timeout, unit);
        } catch (InterruptedException error) {
            interrupted = true;
            Logs.logger().atWarning().withCause(error).log(operation + " interrupted. Continuing shutdown");
            return null;
        } catch (TimeoutException error) {
            Logs.logger().atWarning().withCause(error).log(operation + " timed out. Continuing shutdown");
            return null;
        } catch (ExecutionException error) {
            throw new java.util.concurrent.CompletionException(error.getCause());
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
