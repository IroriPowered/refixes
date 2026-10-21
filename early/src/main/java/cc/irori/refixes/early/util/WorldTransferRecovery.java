package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SpawnUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class WorldTransferRecovery {
    private static final Map<PlayerRef, Transfer> TRANSFERS = new ConcurrentHashMap<>();
    private static final Map<PlayerRef, Admission> ADMISSIONS = new ConcurrentHashMap<>();

    private WorldTransferRecovery() {}

    public static boolean isTransferring(PlayerRef player) {
        return TRANSFERS.containsKey(player) || ADMISSIONS.containsKey(player);
    }

    public static boolean manages(World world, PlayerRef player) {
        Transfer transfer = TRANSFERS.get(player);
        return transfer != null && transfer.destination == world;
    }

    public static boolean hasOtherOwner(World world, PlayerRef player) {
        Transfer transfer = TRANSFERS.get(player);
        Admission admission = ADMISSIONS.get(player);
        return (transfer != null && transfer.destination != world) || (admission != null && admission.world != world);
    }

    public static CompletableFuture<PlayerRef> admit(
            World world, PlayerRef player, Supplier<CompletableFuture<PlayerRef>> nativeAttach) {
        Holder<EntityStore> holder = player.getHolder();
        Admission admission = new Admission(world, holder);
        Transfer transfer = TRANSFERS.get(player);
        if (holder == null
                || player.getReference() != null
                || (transfer != null && transfer.destination == world && transfer.holder != holder)
                || ADMISSIONS.putIfAbsent(player, admission) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player admission ownership changed"));
        }
        try {
            CompletableFuture<PlayerRef> future = nativeAttach.get();
            if (future != null) {
                CompletableFuture<PlayerRef> result = new CompletableFuture<>();
                future.whenComplete((added, failure) -> {
                    ADMISSIONS.remove(player, admission);
                    if (failure == null) result.complete(added);
                    else result.completeExceptionally(failure);
                });
                return result;
            }
            ADMISSIONS.remove(player, admission);
            return null;
        } catch (Throwable failure) {
            ADMISSIONS.remove(player, admission);
            return CompletableFuture.failedFuture(failure);
        }
    }

    public static void checkAdmission(World world, PlayerRef player) {
        Admission admission = ADMISSIONS.get(player);
        if (admission == null
                || admission.world != world
                || admission.holder != player.getHolder()
                || player.getReference() != null) {
            throw new IllegalStateException("Player admission ownership changed");
        }
    }

    public static CompletableFuture<PlayerRef> attach(
            World destination,
            PlayerRef player,
            World source,
            Transform origin,
            Supplier<CompletableFuture<PlayerRef>> nativeAttach) {
        Transfer transfer = new Transfer(player, source, new Transform(origin));
        if (TRANSFERS.putIfAbsent(player, transfer) != null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player transfer is already pending"));
        }
        transfer.attempt(destination, nativeAttach)
                .whenComplete((added, failure) -> transfer.guard(() -> {
                    if (failure == null && added != null) {
                        transfer.finish(added, null);
                    } else {
                        Throwable error = failure != null
                                ? failure
                                : new IllegalStateException("Destination did not accept player");
                        Logs.logger()
                                .atWarning()
                                .withCause(error)
                                .log(
                                        "Failed to transfer player '%s' to world '%s'",
                                        player.getUsername(), destination.getName());
                        transfer.recover(source, transfer.origin, () -> transfer.fallback(error), error);
                    }
                }));
        return transfer.result;
    }

    private record Admission(World world, Holder<EntityStore> holder) {}

    private static final class Transfer {
        final PlayerRef player;
        final World source;
        final Transform origin;
        final Holder<EntityStore> holder;
        final CompletableFuture<PlayerRef> result = new CompletableFuture<>();
        volatile World destination;

        Transfer(PlayerRef player, World source, Transform origin) {
            this.player = player;
            this.source = source;
            this.origin = origin;
            this.holder = player.getHolder();
        }

        CompletableFuture<PlayerRef> attempt(World world, Supplier<CompletableFuture<PlayerRef>> attach) {
            this.destination = world;
            try {
                CompletableFuture<PlayerRef> future = attach.get();
                return future != null ? future : CompletableFuture.completedFuture(null);
            } catch (Throwable failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }

        void recover(World world, Transform transform, Runnable next, Throwable failure) {
            if (this.player.getReference() != null
                    || ADMISSIONS.containsKey(this.player)
                    || !this.player.getPacketHandler().stillActive()) {
                finish(null, failure);
                return;
            }
            if (this.player.getHolder() != this.holder) {
                disconnect(failure);
                return;
            }
            if (world == null || !world.isAlive() || Universe.get().getWorld(world.getName()) != world) {
                next.run();
                return;
            }
            attempt(world, () -> world.addPlayer(this.player, transform, Boolean.TRUE, Boolean.FALSE))
                    .whenComplete((added, error) -> guard(() -> {
                        if (error == null && added != null) finish(null, failure);
                        else next.run();
                    }));
        }

        void fallback(Throwable failure) {
            if (this.player.getReference() != null
                    || ADMISSIONS.containsKey(this.player)
                    || !this.player.getPacketHandler().stillActive()) {
                finish(null, failure);
                return;
            }
            if (this.player.getHolder() != this.holder) {
                disconnect(failure);
                return;
            }
            World fallback = Universe.get().getDefaultWorld();
            if (fallback == null || fallback == this.source || !fallback.isAlive()) {
                disconnect(failure);
                return;
            }
            CompletableFuture<Transform> spawn;
            try {
                spawn = SpawnUtil.resolveSpawnPoint(fallback, this.player.getUuid())
                        .orTimeout(30, TimeUnit.SECONDS);
            } catch (Throwable error) {
                disconnect(failure);
                return;
            }
            spawn.whenComplete((transform, error) -> guard(() -> {
                if (error != null) disconnect(failure);
                else recover(fallback, transform, () -> disconnect(failure), failure);
            }));
        }

        void guard(Runnable action) {
            try {
                action.run();
            } catch (Throwable failure) {
                disconnect(failure);
            }
        }

        void disconnect(Throwable failure) {
            try {
                if (this.player.getReference() == null
                        && !ADMISSIONS.containsKey(this.player)
                        && this.player.getPacketHandler().stillActive()) {
                    this.player.setWorldUuid(this.source.getWorldConfig().getUuid());
                    this.player
                            .getPacketHandler()
                            .disconnect(Message.translation("server.general.disconnect.teleportNoWorld"));
                }
            } finally {
                finish(null, failure);
            }
        }

        void finish(PlayerRef added, Throwable failure) {
            TRANSFERS.remove(this.player, this);
            if (failure == null) this.result.complete(added);
            else this.result.completeExceptionally(failure);
        }
    }
}
