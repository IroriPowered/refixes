package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.RetiredWorlds;
import cc.irori.refixes.early.util.WorldPolicySaveJoin;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
public class MixinWorldShutdown {
    @Redirect(
            method = "saveConfigForShutdown",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"),
            require = 1,
            expect = 1,
            allow = 1)
    private Object refixes$timeoutConfigSave(CompletableFuture<?> future) {
        int timeout = EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.get();
        if (timeout <= 0 || !HytaleServer.get().isShuttingDown()) return future.join();
        Object result = WorldPolicySaveJoin.await(future, timeout, TimeUnit.SECONDS, "World config save");
        if (!future.isDone()) RetiredWorlds.retain((World) (Object) this, future);
        return result;
    }
}
