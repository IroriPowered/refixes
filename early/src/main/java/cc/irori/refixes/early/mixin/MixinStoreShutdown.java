package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.WorldPolicySaveJoin;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Store.class)
public class MixinStoreShutdown {

    @Redirect(
            method = "shutdown0",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"))
    private Object refixes$joinWithTimeout(CompletableFuture<?> future) {
        int timeoutSeconds = EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.get();
        if (timeoutSeconds <= 0 || !HytaleServer.get().isShuttingDown()) return future.join();
        return WorldPolicySaveJoin.await(future, timeoutSeconds, TimeUnit.SECONDS, "Store resource save");
    }
}
