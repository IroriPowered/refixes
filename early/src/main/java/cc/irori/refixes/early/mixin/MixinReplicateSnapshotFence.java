package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.ChunkTrackerSectionFence;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.CachedPacket;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.world.SetChunk;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.systems.ChunkSystems;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSystems.ReplicateChanges.class)
public abstract class MixinReplicateSnapshotFence {

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;getAndClearChangedPositions()Lit/unimi/dsi/fastutil/ints/IntOpenHashSet;"),
            require = 1,
            allow = 1)
    private IntOpenHashSet refixes$refreshCachedSnapshot(
            BlockSection blockSection, Operation<IntOpenHashSet> original) {
        IntOpenHashSet changes = original.call(blockSection);
        if (!changes.isEmpty()) blockSection.invalidate();
        return changes;
    }

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;getCachedChunkPacket(III)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<CachedPacket<SetChunk>> refixes$ticketBeforeRequest(
            BlockSection blockSection,
            int x,
            int y,
            int z,
            Operation<CompletableFuture<CachedPacket<SetChunk>>> original,
            @Share("refixes$ticket") LocalLongRef ticket) {
        ticket.set(ChunkTrackerSectionFence.SEQUENCE.incrementAndGet());
        return original.call(blockSection, x, y, z);
    }

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$fenceSnapshot(
            CompletableFuture<CachedPacket<SetChunk>> future,
            Consumer<CachedPacket<SetChunk>> nativeAction,
            Operation<CompletableFuture<Void>> original,
            @Local ObjectArrayList<PlayerRef> players,
            @Local ChunkSection section,
            @Local(argsOnly = true) Store<ChunkStore> store,
            @Share("refixes$ticket") LocalLongRef ticket) {
        int x = section.getX();
        int y = section.getY();
        int z = section.getZ();
        long captured = ticket.get();
        World world = store.getExternalData().getWorld();
        Consumer<CachedPacket<SetChunk>> fenced = packet -> {
            for (PlayerRef player : players) {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) continue;
                if (ref.getStore() != world.getEntityStore().getStore()) continue;
                ((ChunkTrackerSectionFence) player.getChunkTracker())
                        .refixes$writeSnapshot(captured, player.getPacketHandler(), packet, x, y, z, false);
            }
        };
        return original.call(future, fenced);
    }

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/PlayerRef;getChunkTracker()Lcom/hypixel/hytale/server/core/modules/entity/player/ChunkTracker;"),
            require = 2,
            allow = 2)
    private ChunkTracker refixes$rememberTracker(
            PlayerRef player,
            Operation<ChunkTracker> original,
            @Share("refixes$tracker") LocalRef<ChunkTracker> tracker) {
        ChunkTracker result = original.call(player);
        tracker.set(result);
        return result;
    }

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/io/PacketHandler;writeNoCache(Lcom/hypixel/hytale/protocol/ToClientPacket;)V"),
            require = 2,
            allow = 2)
    private void refixes$orderDelta(
            PacketHandler handler,
            ToClientPacket packet,
            Operation<Void> original,
            @Local ChunkSection section,
            @Share("refixes$tracker") LocalRef<ChunkTracker> tracker) {
        ((ChunkTrackerSectionFence) tracker.get())
                .refixes$writeDelta(handler, packet, section.getX(), section.getY(), section.getZ(), false);
    }

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/modules/entity/player/ChunkTracker;isLoaded(III)Z"),
            require = 2,
            allow = 2)
    private boolean refixes$markSkippedWhileLoading(
            ChunkTracker tracker, int x, int y, int z, Operation<Boolean> original) {
        if (original.call(tracker, x, y, z)) return true;
        if (((ChunkTrackerSectionFence) tracker).refixes$markDirtyIfStillLoading(x, y, z)) return false;
        return original.call(tracker, x, y, z);
    }
}
