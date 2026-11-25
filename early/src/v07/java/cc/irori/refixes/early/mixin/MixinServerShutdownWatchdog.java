package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.server.core.HytaleServer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@IfHytale("<0.8.0-pre.1")
@Mixin(HytaleServer.class)
public abstract class MixinServerShutdownWatchdog {

    @Unique
    private static final long refixes$CONFIG_SAVE_TIMEOUT_SECONDS = 10L;

    @Redirect(
            method = "shutdown0(Lcom/hypixel/hytale/server/core/ShutdownReason;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"),
            require = 1,
            allow = 1)
    private Object refixes$boundConfigSave(CompletableFuture<?> save) throws ExecutionException {
        try {
            return save.get(refixes$CONFIG_SAVE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            Logs.logger()
                    .atWarning()
                    .log(
                            "Config save did not finish within %d s, continuing shutdown",
                            refixes$CONFIG_SAVE_TIMEOUT_SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logs.logger().atWarning().log("Interrupted while saving the config, continuing shutdown");
        }
        return null;
    }

    @Redirect(
            method = "shutdown0(Lcom/hypixel/hytale/server/core/ShutdownReason;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/ScheduledExecutorService;schedule(Ljava/lang/Runnable;JLjava/util/concurrent/TimeUnit;)Ljava/util/concurrent/ScheduledFuture;"),
            require = 1,
            allow = 1)
    private ScheduledFuture<?> refixes$dedicatedHaltWatchdog(
            ScheduledExecutorService executor, Runnable halt, long delay, TimeUnit unit) {
        Thread.ofPlatform().name("ShutdownWatchdog").daemon().start(() -> {
            try {
                unit.sleep(delay);
            } catch (InterruptedException e) {
                return;
            }
            halt.run();
        });
        return null;
    }
}
