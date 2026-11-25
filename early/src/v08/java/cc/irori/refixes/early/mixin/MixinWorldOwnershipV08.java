package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.accessor.TickingThreadAccess;
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

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
@Mixin(World.class)
public abstract class MixinWorldOwnershipV08 {

    @Shadow
    private AtomicBoolean alive;

    @Shadow
    private void stopAcceptingTasks() {}

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

    @WrapMethod(method = "drainPlayersForRemoval(Ljava/util/Map;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<Void> refixes$skipUnstartedDrain(
            Map<UUID, PlayerRef> players, Operation<CompletableFuture<Void>> original) {
        if (players.isEmpty() && ((TickingThreadAccess) (Object) this).refixes$getThread() == null) {
            return CompletableFuture.completedFuture(null);
        }
        return original.call(players);
    }

    @WrapOperation(
            method = "lambda$stopAfter$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/World;stopAsync()Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$stopUnstartedOwner(
            World world, Operation<CompletableFuture<Void>> original) {
        CompletableFuture<?> init;
        synchronized (world) {
            if (((TickingThreadAccess) world).refixes$getThread() != null) return original.call(world);
            synchronized (refixes$initLock) {
                stopAcceptingTasks();
                refixes$retired = true;
                init = refixes$init;
            }
        }
        if (init == null) {
            refixes$releaseGenerators();
            return CompletableFuture.completedFuture(null);
        }
        return init.handle((ignored, error) -> null).thenRun(this::refixes$releaseGenerators);
    }

    @Unique
    private void refixes$releaseGenerators() {
        World world = (World) (Object) this;
        world.getWorldMapManager().shutdown();
        world.getChunkStore().shutdownGenerator();
    }
}
