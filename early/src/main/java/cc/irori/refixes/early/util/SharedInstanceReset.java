package cc.irori.refixes.early.util;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.accessor.SharedInstanceResetAccess;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.removal.InstanceDataResource;
import com.hypixel.hytale.builtin.instances.removal.RemovalCondition;
import com.hypixel.hytale.builtin.instances.removal.WorldEmptyCondition;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class SharedInstanceReset {

    private static final HytaleLogger LOGGER = Logs.logger();
    private static final Map<String, CompletableFuture<Void>> GATES = new ConcurrentHashMap<>();

    private SharedInstanceReset() {}

    private static boolean isEnabled() {
        return EarlyOptions.SHARED_INSTANCES_RESET_ON_EMPTY.get()
                && EarlyOptions.SHARED_INSTANCES_STORAGE_AVAILABLE.get();
    }

    public static void onRemovalDecided(World world) {
        if (!isEnabled()
                || !world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)
                || !(world instanceof SharedInstanceResetAccess access)
                || access.refixes$isResetArmed()
                || access.refixes$resetGate().get() != null
                || !access.refixes$isVisited()
                || world.getPlayerCount() > 0
                || !removesWhenEmpty(InstanceWorldConfig.get(world.getWorldConfig()))) {
            return;
        }
        CompletableFuture<Void> gate = new CompletableFuture<>();
        if (GATES.putIfAbsent(key(world.getName()), gate) != null) return;
        access.refixes$resetGate().set(gate);
    }

    public static CompletableFuture<Boolean> removeAsync(String name, Supplier<CompletableFuture<Boolean>> removal) {
        World world = Universe.get().getWorld(name);
        if (!(world instanceof SharedInstanceResetAccess access)) return removal.get();
        CompletableFuture<Void> gate = access.refixes$resetGate().getAndSet(null);
        if (gate == null) return removal.get();
        access.refixes$setResetArmed(true);
        CompletableFuture<Boolean> removed;
        try {
            removed = removal.get();
        } catch (Throwable error) {
            restart(world, access, gate);
            throw error;
        }
        removed.whenComplete((result, error) -> {
            if (error == null && Boolean.TRUE.equals(result)) complete(world.getName(), gate);
            else restart(world, access, gate);
        });
        return removed;
    }

    public static void deleteAfterStop(World world, Runnable validateDeleteOnRemove) {
        if (!(world instanceof SharedInstanceResetAccess access)
                || !access.refixes$isResetArmed()
                || !isEnabled()
                || world.getFailureException() != null
                || HytaleServer.get().isShuttingDown()) {
            validateDeleteOnRemove.run();
            return;
        }
        LOGGER.atInfo().log("Resetting shared instance world '%s' after its players left", world.getName());
        WorldConfig config = world.getWorldConfig();
        config.setDeleteOnRemove(true);
        try {
            validateDeleteOnRemove.run();
        } finally {
            config.setDeleteOnRemove(false);
        }
    }

    public static void release(World world) {
        if (!(world instanceof SharedInstanceResetAccess access)) return;
        CompletableFuture<Void> gate = access.refixes$resetGate().getAndSet(null);
        complete(world.getName(), gate);
    }

    public static CompletableFuture<Void> gate(String name) {
        return name == null ? null : GATES.get(key(name));
    }

    private static void complete(String name, CompletableFuture<Void> gate) {
        if (gate == null) return;
        GATES.remove(key(name), gate);
        gate.complete(null);
    }

    private static void restart(World world, SharedInstanceResetAccess access, CompletableFuture<Void> gate) {
        access.refixes$setResetArmed(false);
        if (!world.isAlive()) {
            complete(world.getName(), gate);
            return;
        }
        Runnable restart = () -> {
            try {
                InstanceDataResource data =
                        world.getChunkStore().getStore().getResource(InstanceDataResource.getResourceType());
                data.setRemoving(false);
                data.setHadPlayer(false);
                data.setWorldTimeoutTimer(null);
                access.refixes$setVisited(world.getPlayerCount() > 0);
            } finally {
                complete(world.getName(), gate);
            }
        };
        if (world.isInThread()) {
            restart.run();
            return;
        }
        try {
            world.execute(restart);
        } catch (RuntimeException rejected) {
            complete(world.getName(), gate);
        }
    }

    private static boolean removesWhenEmpty(InstanceWorldConfig config) {
        if (config == null) return false;
        for (RemovalCondition condition : config.getRemovalConditions()) {
            if (condition instanceof WorldEmptyCondition) return true;
        }
        return false;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
