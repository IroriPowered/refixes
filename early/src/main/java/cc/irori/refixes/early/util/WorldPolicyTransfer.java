package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class WorldPolicyTransfer {
    private static final Map<PlayerRef, Pending> PENDING = new IdentityHashMap<>();
    private static final Map<WorldKey, Boolean> CLOSED_WORLDS = new HashMap<>();
    private static final ReferenceQueue<World> STALE_WORLDS = new ReferenceQueue<>();

    private WorldPolicyTransfer() {}

    public static CompletableFuture<PlayerRef> awaitRemovalAndAttach(
            World destination,
            PlayerRef player,
            Ref<EntityStore> initialReference,
            Supplier<CompletableFuture<PlayerRef>> nativeAttach) {
        return awaitRemovalAndAttach(
                destination, player, initialReference, nativeAttach, HytaleServer.SCHEDULED_EXECUTOR, 100L);
    }

    public static CompletableFuture<PlayerRef> awaitRemovalAndAttach(
            World destination,
            PlayerRef player,
            Ref<EntityStore> initialReference,
            Supplier<CompletableFuture<PlayerRef>> nativeAttach,
            ScheduledExecutorService scheduler,
            long timeoutMillis) {
        if (initialReference == null) return nativeAttach.get();
        Pending pending;
        try {
            pending = new Pending(destination, player, initialReference, nativeAttach, timeoutMillis);
        } catch (RuntimeException | Error failure) {
            return CompletableFuture.failedFuture(failure);
        }
        synchronized (PENDING) {
            if (isClosed(pending.source) || isClosed(destination)) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Player transfer world is shutting down"));
            }
            if (PENDING.putIfAbsent(player, pending) != null) {
                return CompletableFuture.failedFuture(new IllegalStateException("Player transfer is already pending"));
            }
        }
        pending.result.whenComplete((added, failure) -> {
            pending.cancelPoll();
            pending.release();
        });
        try {
            pending.setPoll(scheduler.scheduleAtFixedRate(pending::check, 0, 10, TimeUnit.MILLISECONDS));
        } catch (RuntimeException | Error failure) {
            pending.result.completeExceptionally(failure);
        }
        return pending.result;
    }

    public static void closeWorld(World world) {
        List<Pending> cancelled;
        synchronized (PENDING) {
            removeStaleWorlds();
            CLOSED_WORLDS.put(new WorldKey(world, STALE_WORLDS), Boolean.TRUE);
            cancelled = PENDING.values().stream()
                    .filter(pending -> pending.source == world || pending.destination == world)
                    .toList();
        }
        cancelled.forEach(pending -> pending.rejectBeforeAttach("Player transfer world is shutting down"));
    }

    private static boolean isClosed(World world) {
        removeStaleWorlds();
        return CLOSED_WORLDS.containsKey(new WorldKey(world, null));
    }

    private static void removeStaleWorlds() {
        WorldKey stale;
        while ((stale = (WorldKey) STALE_WORLDS.poll()) != null) CLOSED_WORLDS.remove(stale);
    }

    private static final class WorldKey extends WeakReference<World> {
        private final int hash;

        WorldKey(World world, ReferenceQueue<World> queue) {
            super(world, queue);
            this.hash = System.identityHashCode(world);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            World world = get();
            return other instanceof WorldKey key && world != null && world == key.get();
        }
    }

    private static final class Pending {
        final World destination;
        final World source;
        final PlayerRef player;
        final Ref<EntityStore> initialReference;
        final Supplier<CompletableFuture<PlayerRef>> nativeAttach;
        final CompletableFuture<PlayerRef> result = new CompletableFuture<>();
        private final long startedAt = System.nanoTime();
        private final long timeoutNanos;
        private ScheduledFuture<?> poll;
        private boolean ownerQueued;
        private boolean handedOff;

        Pending(
                World destination,
                PlayerRef player,
                Ref<EntityStore> initialReference,
                Supplier<CompletableFuture<PlayerRef>> nativeAttach,
                long timeoutMillis) {
            this.destination = destination;
            this.source = initialReference.getStore().getExternalData().getWorld();
            this.player = player;
            this.initialReference = initialReference;
            this.nativeAttach = nativeAttach;
            this.timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        }

        synchronized void setPoll(ScheduledFuture<?> task) {
            this.poll = task;
            if (this.result.isDone() || this.handedOff) task.cancel(false);
        }

        synchronized void cancelPoll() {
            if (this.poll != null) this.poll.cancel(false);
        }

        void release() {
            synchronized (PENDING) {
                PENDING.remove(this.player, this);
            }
        }

        synchronized void rejectBeforeAttach(String reason) {
            if (!this.handedOff) this.result.completeExceptionally(new IllegalStateException(reason));
        }

        private boolean canProceed() {
            return !this.result.isDone() && !this.handedOff;
        }

        synchronized void check() {
            if (!canProceed() || this.ownerQueued) return;
            this.ownerQueued = true;
            boolean overdue = System.nanoTime() - this.startedAt >= this.timeoutNanos;
            try {
                this.source.execute(() -> onSource(overdue));
            } catch (RuntimeException | Error failure) {
                this.result.completeExceptionally(failure);
            }
        }

        synchronized void onSource(boolean overdue) {
            if (!canProceed()) return;
            try {
                Ref<EntityStore> reference = this.player.getReference();
                if (reference == this.initialReference) {
                    if (overdue) {
                        this.result.completeExceptionally(
                                new IllegalStateException("Player removal did not finish before transfer timeout"));
                        return;
                    }
                    this.ownerQueued = false;
                    return;
                }
                if (reference != null || this.player.getHolder() == null) {
                    this.result.completeExceptionally(
                            new IllegalStateException("Player ownership changed during transfer"));
                    return;
                }
                if (!this.destination.isAlive()) {
                    this.result.completeExceptionally(new IllegalStateException("Destination world has shut down"));
                    return;
                }
                this.destination.execute(this::onDestination);
            } catch (RuntimeException | Error failure) {
                this.result.completeExceptionally(failure);
            }
        }

        void onDestination() {
            synchronized (this) {
                if (!canProceed()) return;
                if (!this.destination.isAlive()
                        || this.player.getReference() != null
                        || this.player.getHolder() == null) {
                    this.result.completeExceptionally(
                            new IllegalStateException("Player or destination changed before transfer"));
                    return;
                }
                synchronized (PENDING) {
                    if (!isClosed(this.source) && !isClosed(this.destination)) {
                        this.handedOff = true;
                        PENDING.remove(this.player, this);
                    }
                }
                if (!this.handedOff) {
                    rejectBeforeAttach("Player transfer world is shutting down");
                    return;
                }
                cancelPoll();
            }
            try {
                CompletableFuture<PlayerRef> attached = this.nativeAttach.get();
                if (attached == null) {
                    this.result.complete(null);
                } else {
                    attached.whenComplete((added, failure) -> {
                        if (failure == null) this.result.complete(added);
                        else this.result.completeExceptionally(failure);
                    });
                }
            } catch (RuntimeException | Error failure) {
                this.result.completeExceptionally(failure);
            }
        }
    }
}
