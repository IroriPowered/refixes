package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.WorldLoadCancelledException;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
@Mixin(Universe.class)
public abstract class MixinUniverseWorldOwnershipV08 {

    @Shadow
    private Map<String, World> worlds;

    @Unique
    private static final ThreadLocal<AtomicReference<World>> refixes$creating = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<World> refixes$failedOwner = new ThreadLocal<>();

    @WrapOperation(
            method = {
                "makeWorld(Ljava/lang/String;Ljava/nio/file/Path;Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;Z)Ljava/util/concurrent/CompletableFuture;",
                "addWorld(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",
                "loadWorld(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;"
            },
            at = @At(value = "INVOKE", target = "Ljava/util/Map;containsKey(Ljava/lang/Object;)Z"),
            require = 3,
            allow = 3)
    private boolean refixes$canonicalExistingWorld(Map<String, World> map, Object key, Operation<Boolean> original) {
        return original.call(map, ((String) key).toLowerCase(Locale.ROOT));
    }

    @WrapOperation(
            method =
                    "makeWorld(Ljava/lang/String;Ljava/nio/file/Path;Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;Z)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<World> refixes$captureAttempt(
            Supplier<World> supplier,
            Operation<CompletableFuture<World>> original,
            @Share("owner") LocalRef<AtomicReference<World>> owner) {
        AtomicReference<World> attempt = new AtomicReference<>();
        owner.set(attempt);
        return original.call((Supplier<World>) () -> {
            AtomicReference<World> previous = refixes$creating.get();
            refixes$creating.set(attempt);
            try {
                return supplier.get();
            } finally {
                if (previous == null) refixes$creating.remove();
                else refixes$creating.set(previous);
            }
        });
    }

    @WrapOperation(
            method = "lambda$makeWorld$0",
            at = @At(value = "NEW", target = "com/hypixel/hytale/server/core/universe/world/World"),
            require = 1,
            allow = 1)
    private World refixes$captureOwner(String name, Path path, WorldConfig config, Operation<World> original) {
        World world = original.call(name, path, config);
        AtomicReference<World> attempt = refixes$creating.get();
        if (attempt != null) attempt.set(world);
        return world;
    }

    @WrapOperation(
            method =
                    "makeWorld(Ljava/lang/String;Ljava/nio/file/Path;Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;Z)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;whenComplete(Ljava/util/function/BiConsumer;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<World> refixes$cleanOnlyOwnedAttempt(
            CompletableFuture<World> future,
            BiConsumer<World, Throwable> callback,
            Operation<CompletableFuture<World>> original,
            @Share("owner") LocalRef<AtomicReference<World>> owner) {
        AtomicReference<World> attempt = owner.get();
        return future.whenComplete((world, error) -> {
                    World failed = attempt.get();
                    if (error == null
                            || failed == null
                            || worlds.get(failed.getName().toLowerCase(Locale.ROOT)) != failed) return;
                    World previous = refixes$failedOwner.get();
                    refixes$failedOwner.set(failed);
                    try {
                        ((Universe) (Object) this).removeWorldExceptionally(failed.getName(), Map.of());
                    } catch (Exception cleanupError) {
                        Logs.logger()
                                .atWarning()
                                .withCause(cleanupError)
                                .log("Failed to clean up world '%s' after init failure", failed.getName());
                    } finally {
                        if (previous == null) refixes$failedOwner.remove();
                        else refixes$failedOwner.set(previous);
                    }
                })
                .copy();
    }

    @WrapOperation(
            method =
                    "makeWorld(Ljava/lang/String;Ljava/nio/file/Path;Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;Z)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenCompose(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;",
                            ordinal = 1),
            require = 1,
            allow = 1)
    private CompletableFuture<World> refixes$startOnlyOwnedWorld(
            CompletableFuture<World> future,
            Function<World, CompletionStage<World>> start,
            Operation<CompletableFuture<World>> original) {
        return original.call(future, (Function<World, CompletionStage<World>>) world -> {
            synchronized (world) {
                if (worlds.get(world.getName().toLowerCase(Locale.ROOT)) != world || !world.isAlive()) {
                    return CompletableFuture.failedFuture(new WorldLoadCancelledException());
                }
                return start.apply(world);
            }
        });
    }

    @WrapOperation(
            method = "removeWorldExceptionally(Ljava/lang/String;Ljava/util/Map;)V",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;"),
            require = 1,
            allow = 1)
    private Object refixes$removeFailedOwner(Map<String, World> map, Object key, Operation<Object> original) {
        World expected = refixes$failedOwner.get();
        if (expected != null && expected.getName().toLowerCase(Locale.ROOT).equals(key)) return expected;
        return original.call(map, key);
    }
}
