package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.ChunkTrackerSectionFence;
import com.hypixel.hytale.builtin.fluid.FluidSystems;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.CachedPacket;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.world.SetFluids;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalLongRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FluidSystems.ReplicateChanges.class)
public abstract class MixinFluidReplicateSnapshotFence {

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/FluidSection;getCachedPacket()Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<CachedPacket<SetFluids>> refixes$ticketBeforeRequest(
            FluidSection section,
            Operation<CompletableFuture<CachedPacket<SetFluids>>> original,
            @Share("refixes$ticket") LocalLongRef ticket) {
        ticket.set(ChunkTrackerSectionFence.SEQUENCE.incrementAndGet());
        return original.call(section);
    }

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;whenComplete(Ljava/util/function/BiConsumer;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<CachedPacket<SetFluids>> refixes$fenceSnapshot(
            CompletableFuture<CachedPacket<SetFluids>> future,
            BiConsumer<CachedPacket<SetFluids>, Throwable> nativeAction,
            Operation<CompletableFuture<CachedPacket<SetFluids>>> original,
            @Local ObjectArrayList<PlayerRef> players,
            @Local FluidSection section,
            @Local World world,
            @Share("refixes$ticket") LocalLongRef ticket) {
        int x = section.getX();
        int y = section.getY();
        int z = section.getZ();
        long captured = ticket.get();
        BiConsumer<CachedPacket<SetFluids>, Throwable> fenced = (packet, error) -> {
            if (error != null) {
                nativeAction.accept(packet, error);
                return;
            }
            for (PlayerRef player : players) {
                Ref<EntityStore> ref = player.getReference();
                if (ref == null || !ref.isValid()) continue;
                if (ref.getStore() != world.getEntityStore().getStore()) continue;
                ((ChunkTrackerSectionFence) player.getChunkTracker())
                        .refixes$writeSnapshot(captured, player.getPacketHandler(), packet, x, y, z, true);
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
            @Local FluidSection section,
            @Share("refixes$tracker") LocalRef<ChunkTracker> tracker) {
        ((ChunkTrackerSectionFence) tracker.get())
                .refixes$writeDelta(handler, packet, section.getX(), section.getY(), section.getZ(), true);
    }
}
