package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Store.class)
public class MixinStoreShutdown {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Redirect(
            method = "shutdown0",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"))
    private Object refixes$joinWithTimeout(CompletableFuture<?> future) {
        int timeoutSeconds = EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.get();
        boolean wasInterrupted = Thread.interrupted();
        try {
            return timeoutSeconds <= 0 ? future.join() : future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            refixes$LOGGER.atWarning().log(
                    "Store#shutdown0(): saveAllResources timed out after " + timeoutSeconds + "s, continuing shutdown");
            return null;
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "Store#shutdown0(): saveAllResources failed, continuing shutdown");
            return null;
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
