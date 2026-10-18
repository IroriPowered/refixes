package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.WorldPolicySaveJoin;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(World.class)
public abstract class MixinWorldShutdown {
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
    private Object refixes$timeoutConfigSave(CompletableFuture<?> future) {
        int timeout = EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.get();
        if (timeout <= 0) return future.join();
        return WorldPolicySaveJoin.await(future, timeout, TimeUnit.SECONDS, "World config save");
    }
}
