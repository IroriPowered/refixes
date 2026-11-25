package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.RetiredWorlds;
import cc.irori.refixes.early.util.WorldPolicySaveJoin;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
public abstract class MixinWorldShutdown {
    @IfHytale("<0.8.0-pre.1")
    @Redirect(
            method = "onShutdown",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"),
            require = 2,
            expect = 2,
            allow = 2,
            slice =
                    @org.spongepowered.asm.mixin.injection.Slice(
                            from =
                                    @At(
                                            value = "FIELD",
                                            target =
                                                    "Lcom/hypixel/hytale/server/core/universe/world/World;savePath:Ljava/nio/file/Path;",
                                            ordinal = 0),
                            to =
                                    @At(
                                            value = "FIELD",
                                            target =
                                                    "Lcom/hypixel/hytale/server/core/universe/world/World;acceptingTasks:Ljava/util/concurrent/atomic/AtomicBoolean;")))
    private Object refixes$timeoutShutdownSave(CompletableFuture<?> future) {
        return refixes$awaitSave(future);
    }

    @IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
    @Redirect(
            method = "saveConfigForShutdown",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"),
            require = 1,
            expect = 1,
            allow = 1)
    private Object refixes$timeoutConfigSave(CompletableFuture<?> future) {
        return refixes$awaitSave(future);
    }

    @Unique
    private Object refixes$awaitSave(CompletableFuture<?> future) {
        int timeout = EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.get();
        if (timeout <= 0 || !HytaleServer.get().isShuttingDown()) return future.join();
        Object result = WorldPolicySaveJoin.await(future, timeout, TimeUnit.SECONDS, "World config save");
        if (!future.isDone()) RetiredWorlds.retain((World) (Object) this, future);
        return result;
    }
}
