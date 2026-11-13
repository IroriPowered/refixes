package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.TickingThreadAccess;
import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.RetiredWorlds;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.WorldLoadCancelledException;
import com.hypixel.hytale.server.core.universe.world.World;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorldOwnership {

    @Shadow
    private AtomicBoolean alive;

    @Shadow
    private AtomicBoolean acceptingTasks;

    @Unique
    private final Object refixes$initLock = new Object();

    @Unique
    private CompletableFuture<?> refixes$init;

    @Unique
    private volatile boolean refixes$retired;

    @WrapMethod(method = "init()Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<World> refixes$initOwnedWorld(Operation<CompletableFuture<World>> original) {
        synchronized (refixes$initLock) {
            if (!alive.get()) return CompletableFuture.failedFuture(new WorldLoadCancelledException());
            CompletableFuture<World> init = original.call();
            refixes$init = init;
            return init;
        }
    }

    @WrapOperation(
            method = "init()Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<World> refixes$releaseLateGenerators(
            Supplier<World> load, Operation<CompletableFuture<World>> original) {
        CompletableFuture<World> generators = original.call(load);
        generators.whenComplete((world, error) -> {
            if (refixes$retired) refixes$releaseGenerators();
        });
        return generators;
    }

    @Inject(method = "onShutdown", at = @At("HEAD"))
    private void refixes$retireOnShutdown(CallbackInfo ci) {
        refixes$retired = true;
    }

    @WrapMethod(method = "stopIndividualWorld(Ljava/util/Map;)V")
    private void refixes$stopUnstartedOwner(Map<UUID, PlayerRef> players, Operation<Void> original) {
        World world = (World) (Object) this;
        Thread thread = ((TickingThreadAccess) world).refixes$getThread();
        if (thread == null || !thread.isAlive()) {
            CompletableFuture<?> init;
            synchronized (refixes$initLock) {
                alive.set(false);
                acceptingTasks.set(false);
                refixes$retired = true;
                init = refixes$init;
            }
            if (init == null) {
                refixes$releaseGenerators();
                return;
            }
            CompletableFuture<Void> released =
                    init.handle((ignored, error) -> null).thenRun(() -> refixes$releaseGenerators());
            RetiredWorlds.retainOwned(world, null, released);
            released.exceptionally(failure -> {
                Logs.logger()
                        .atSevere()
                        .withCause(failure)
                        .log("Could not release generators of world '%s'", world.getName());
                return null;
            });
            return;
        }
        original.call(players);
    }

    @Unique
    private void refixes$releaseGenerators() {
        World world = (World) (Object) this;
        world.getWorldMapManager().stop();
        world.getChunkStore().shutdownGenerator();
    }
}
