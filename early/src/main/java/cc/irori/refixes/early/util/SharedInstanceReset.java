package cc.irori.refixes.early.util;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.accessor.SharedInstanceResetAccess;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.removal.InstanceDataResource;
import com.hypixel.hytale.builtin.instances.removal.RemovalCondition;
import com.hypixel.hytale.builtin.instances.removal.WorldEmptyCondition;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public final class SharedInstanceReset {

    private static final HytaleLogger LOGGER = Logs.logger();
    private static final Map<String, CompletableFuture<Void>> GATES = new ConcurrentHashMap<>();
    private static final ThreadLocal<World> REMOVING = new ThreadLocal<>();

    private SharedInstanceReset() {}

    private static boolean isEnabled() {
        return EarlyOptions.SHARED_INSTANCES_RESET_ON_EMPTY.get()
                && EarlyOptions.SHARED_INSTANCES_STORAGE_AVAILABLE.get();
    }

    public static void onRemovalDecided(World world) {
        if (!isEnabled()
                || !world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)
                || !(world instanceof SharedInstanceResetAccess access)
                || !access.refixes$isVisited()
                || world.getPlayerCount() > 0
                || !removesWhenEmpty(InstanceWorldConfig.get(world.getWorldConfig()))) {
            return;
        }
        access.refixes$setResetArmed(true);
    }

    public static boolean remove(World world, BooleanSupplier removal) {
        if (!(world instanceof SharedInstanceResetAccess access) || !access.refixes$isResetArmed()) {
            return removal.getAsBoolean();
        }
        CompletableFuture<Void> gate = new CompletableFuture<>();
        if (GATES.putIfAbsent(key(world.getName()), gate) != null) {
            access.refixes$setResetArmed(false);
            return removal.getAsBoolean();
        }
        access.refixes$resetGate().set(gate);
        World previous = REMOVING.get();
        REMOVING.set(world);
        boolean removed = false;
        try {
            removed = removal.getAsBoolean();
            return removed;
        } finally {
            if (previous == null) REMOVING.remove();
            else REMOVING.set(previous);
            access.refixes$setResetArmed(false);
            if (removed || !world.isAlive()) release(world);
            else restartRemoval(world, access);
        }
    }

    public static void resetBeforeUnregister(World world) {
        if (REMOVING.get() != world
                || !(world instanceof SharedInstanceResetAccess access)
                || !access.refixes$isResetArmed()) {
            return;
        }
        access.refixes$setResetArmed(false);
        if (!isEnabled()
                || world.isAlive()
                || world.getFailureException() != null
                || HytaleServer.get().isShuttingDown()) {
            return;
        }
        LOGGER.atInfo().log("Resetting shared instance world '%s' after its players left", world.getName());
        WorldConfig config = world.getWorldConfig();
        config.setDeleteOnRemove(true);
        try {
            world.validateDeleteOnRemove();
        } finally {
            config.setDeleteOnRemove(false);
        }
    }

    public static void release(World world) {
        if (world instanceof SharedInstanceResetAccess access) {
            complete(world, access.refixes$resetGate().getAndSet(null));
        }
    }

    public static void releaseCleanup(World world) {
        if (world instanceof SharedInstanceResetAccess access) {
            complete(world, access.refixes$cleanupGate().getAndSet(null));
        }
    }

    private static void complete(World world, CompletableFuture<Void> gate) {
        if (gate == null) return;
        GATES.remove(key(world.getName()), gate);
        gate.complete(null);
    }

    public static CompletableFuture<Void> gate(String name) {
        return name == null ? null : GATES.get(key(name));
    }

    private static void restartRemoval(World world, SharedInstanceResetAccess access) {
        CompletableFuture<Void> gate = access.refixes$resetGate().getAndSet(null);
        if (gate == null) return;
        access.refixes$cleanupGate().set(gate);
        Runnable restart = () -> {
            if (!access.refixes$cleanupGate().compareAndSet(gate, null)) return;
            try {
                InstanceDataResource data =
                        world.getChunkStore().getStore().getResource(InstanceDataResource.getResourceType());
                data.setRemoving(false);
                data.setHadPlayer(false);
                data.setWorldTimeoutTimer(null);
                access.refixes$setVisited(world.getPlayerCount() > 0);
            } finally {
                complete(world, gate);
            }
        };
        if (world.isInThread()) {
            restart.run();
            return;
        }
        try {
            world.execute(restart);
        } catch (RuntimeException rejected) {
            releaseCleanup(world);
        }
        if (access.refixes$isShutDown()) releaseCleanup(world);
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
