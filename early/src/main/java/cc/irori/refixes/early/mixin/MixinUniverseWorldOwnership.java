package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.TickingThreadAccess;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.WorldLoadCancelledException;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

@Mixin(Universe.class)
public abstract class MixinUniverseWorldOwnership {

    @Shadow
    private Map<String, World> worlds;

    @Shadow
    private Map<UUID, World> worldsByUuid;

    @Unique
    private static final ThreadLocal<AtomicReference<World>> refixes$creating = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<World> refixes$failedOwner = new ThreadLocal<>();

    @Unique
    private final Set<World> refixes$removing =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

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
            method = "lambda$makeWorld$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/Map;putIfAbsent(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            ordinal = 0),
            require = 1,
            allow = 1)
    private Object refixes$registerOwnedIndices(
            Map<String, World> map, Object key, Object value, Operation<Object> original) {
        World world = (World) value;
        synchronized (world) {
            Object previous = original.call(map, key, world);
            if (previous != null) return previous;
            UUID uuid = world.getWorldConfig().getUuid();
            World previousUuid = worldsByUuid.putIfAbsent(uuid, world);
            if (previousUuid != null) {
                throw new java.util.ConcurrentModificationException("World with UUID " + uuid
                        + " already exists but didn't before! Looks like you have a race condition.");
            }
            return null;
        }
    }

    @WrapOperation(
            method = "lambda$makeWorld$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/Map;putIfAbsent(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            ordinal = 1),
            require = 1,
            allow = 1)
    private Object refixes$verifyOwnedIndices(
            Map<UUID, World> map, Object uuid, Object value, Operation<Object> original) {
        World world = (World) value;
        synchronized (world) {
            if (map.get(uuid) != world || worlds.get(world.getName().toLowerCase(Locale.ROOT)) != world) {
                throw new WorldLoadCancelledException();
            }
            return null;
        }
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
                    if (error == null || failed == null) return;
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
                        Thread thread = ((TickingThreadAccess) failed).refixes$getThread();
                        if (thread == null || !thread.isAlive()) failed.stopIndividualWorld(Map.of());
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
                if (worlds.get(world.getName().toLowerCase(Locale.ROOT)) != world || refixes$removing.contains(world)) {
                    return CompletableFuture.failedFuture(new WorldLoadCancelledException());
                }
                return start.apply(world);
            }
        });
    }

    @WrapMethod(method = "removeWorld")
    private boolean refixes$removeOwnerOnce(String name, Operation<Boolean> original) {
        java.util.Objects.requireNonNull(name, "Name can't be null!");
        World world = worlds.get(name.toLowerCase(Locale.ROOT));
        if (world == null) return original.call(name);
        synchronized (world) {
            if (!refixes$removing.add(world)) return false;
        }
        try {
            if (worlds.get(name.toLowerCase(Locale.ROOT)) != world) return false;
            return original.call(name);
        } finally {
            refixes$removing.remove(world);
        }
    }

    @WrapMethod(method = "removeWorldExceptionally")
    private void refixes$removeFailedOwnerOnce(String name, Map<UUID, PlayerRef> players, Operation<Void> original) {
        java.util.Objects.requireNonNull(name, "Name can't be null!");
        World expected = refixes$failedOwner.get();
        if (expected != null && !expected.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))
            expected = null;
        World world = expected != null ? expected : worlds.get(name.toLowerCase(Locale.ROOT));
        if (world == null) {
            original.call(name, players);
            return;
        }
        synchronized (world) {
            if (!refixes$removing.add(world)) return;
        }
        try {
            if (worlds.get(name.toLowerCase(Locale.ROOT)) == world) original.call(name, players);
        } finally {
            refixes$removing.remove(world);
        }
    }

    @WrapOperation(
            method = {"removeWorld", "removeWorldExceptionally"},
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;",
                            ordinal = 1),
            require = 2,
            allow = 2)
    private Object refixes$removeOwnedUuid(
            Map<UUID, World> map, Object uuid, Operation<Object> original, @Local World world) {
        boolean[] removed = {false};
        map.computeIfPresent((UUID) uuid, (key, current) -> {
            if (current != world) return current;
            removed[0] = true;
            return null;
        });
        return removed[0] ? world : null;
    }
}
