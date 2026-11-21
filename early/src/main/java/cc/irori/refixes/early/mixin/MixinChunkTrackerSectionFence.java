package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.ChunkTrackerSectionFence;
import cc.irori.refixes.early.duck.QuicheDrainSignal;
import cc.irori.refixes.early.duck.TrackedColumnSectionFence;
import com.hypixel.hytale.math.data.Int3OpenHashSet;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.NetworkChannel;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.io.ChannelConnection;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.StampedLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkTracker.class)
public abstract class MixinChunkTrackerSectionFence implements ChunkTrackerSectionFence {

    @Shadow
    @Final
    private StampedLock lock;

    @Shadow
    @Final
    private Long2ObjectMap<Object> trackedColumns;

    @Shadow
    @Final
    private Int3OpenHashSet reloadingSections;

    @Override
    public void refixes$writeSnapshot(
            long ticket, PacketHandler handler, ToClientPacket packet, int x, int y, int z, boolean fluid) {
        boolean write;
        long stamp = lock.writeLock();
        try {
            write = refixes$fenceSnapshot(ticket, x, y, z, null, null, fluid);
        } finally {
            lock.unlockWrite(stamp);
        }
        if (write) refixes$writeFenced(new Write(this, handler, packet, ticket, x, y, z, true, fluid));
    }

    @Unique
    private boolean refixes$fenceSnapshot(
            long ticket, int x, int y, int z, Runnable send, PacketHandler handler, boolean fluid) {
        Object column = trackedColumns.get(ChunkUtil.indexChunk(x, z));
        if (column == null) return false;
        TrackedColumnSectionFence fence = (TrackedColumnSectionFence) column;
        int decision = fence.refixes$decideSnapshot(y, ticket, fluid);
        if (decision == TrackedColumnSectionFence.WRITE) {
            if (send != null) {
                if (refixes$chunksBacklogged(handler)) {
                    reloadingSections.add(x, y, z);
                    return false;
                }
                send.run();
                fence.refixes$commitSnapshot(y, ticket, fluid);
            }
            return true;
        }
        if (decision == TrackedColumnSectionFence.RELOAD) {
            reloadingSections.add(x, y, z);
        } else if (decision == TrackedColumnSectionFence.DIRTY) {
            fence.refixes$markDirtyIfLoading(y);
        }
        return false;
    }

    @Unique
    private static void refixes$writeFenced(Write write) {
        Write previous = PENDING.get();
        PENDING.set(write);
        try {
            write.handler().writeNoCache(write.packet());
        } finally {
            PENDING.set(previous);
        }
    }

    @Override
    public void refixes$send(Write write, Runnable send) {
        long stamp = lock.writeLock();
        try {
            if (write.snapshot()) {
                refixes$fenceSnapshot(
                        write.ticket(), write.x(), write.y(), write.z(), send, write.handler(), write.fluid());
                return;
            }
            Object column = trackedColumns.get(ChunkUtil.indexChunk(write.x(), write.z()));
            if (column == null
                    || !((TrackedColumnSectionFence) column)
                            .refixes$noteDelta(write.y(), SEQUENCE.incrementAndGet(), write.fluid())) {
                return;
            }
            if (refixes$chunksBacklogged(write.handler())) {
                reloadingSections.add(write.x(), write.y(), write.z());
                return;
            }
            send.run();
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Unique
    private static boolean refixes$chunksBacklogged(PacketHandler handler) {
        if (handler == null) return false;
        ChannelConnection channel = handler.getChannel(NetworkChannel.Chunks);
        return channel instanceof QuicheDrainSignal signal && signal.refixes$backlogged();
    }

    @Override
    public boolean refixes$markDirtyIfStillLoading(int x, int y, int z) {
        long read = lock.readLock();
        try {
            Object column = trackedColumns.get(ChunkUtil.indexChunk(x, z));
            if (column == null || !((TrackedColumnSectionFence) column).refixes$isLoading(y)) return false;
        } finally {
            lock.unlockRead(read);
        }
        long stamp = lock.writeLock();
        try {
            Object column = trackedColumns.get(ChunkUtil.indexChunk(x, z));
            return column != null && ((TrackedColumnSectionFence) column).refixes$markDirtyIfLoading(y);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    @Inject(
            method = "lambda$_loadSectionAsync$1",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/StampedLock;unlockWrite(J)V", ordinal = 0),
            require = 1)
    private void refixes$reloadIfDirty(
            ObjectArrayList<?> futurePackets,
            List<?> packets,
            long chunkIndex,
            @Coerce Object trackedColumn,
            int y,
            com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
            Void unused,
            CallbackInfo ci) {
        if (trackedColumns.get(chunkIndex) != trackedColumn) return;
        if (((TrackedColumnSectionFence) trackedColumn).refixes$takeDirty(y)) {
            reloadingSections.add(ChunkUtil.xOfChunkIndex(chunkIndex), y, ChunkUtil.zOfChunkIndex(chunkIndex));
        }
    }

    @Override
    public void refixes$writeDelta(PacketHandler handler, ToClientPacket packet, int x, int y, int z, boolean fluid) {
        refixes$writeFenced(new Write(this, handler, packet, 0L, x, y, z, false, fluid));
    }

    @Inject(method = "tryLoadSectionAsync", at = @At("HEAD"))
    private void refixes$markLoadStart(
            com.hypixel.hytale.server.core.universe.world.storage.ChunkStore chunkStore,
            com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
            int x,
            int y,
            int z,
            com.hypixel.hytale.server.core.modules.entity.component.TransformComponent transform,
            com.hypixel.hytale.component.ComponentAccessor<?> accessor,
            CallbackInfo ci) {
        Object column = trackedColumns.get(ChunkUtil.indexChunk(x, z));
        if (column != null) {
            ((TrackedColumnSectionFence) column).refixes$markLoadStart(y, SEQUENCE.incrementAndGet());
        }
    }

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/modules/entity/player/ChunkTracker;_loadSectionAsync(IIILcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/universe/world/storage/ChunkStore;Lcom/hypixel/hytale/server/core/modules/entity/player/ChunkTracker$TrackedColumn;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$markEarlyLoadStart(
            ChunkTracker tracker,
            int x,
            int y,
            int z,
            com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
            com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> ref,
            com.hypixel.hytale.server.core.universe.world.storage.ChunkStore chunkStore,
            @Coerce Object column,
            Operation<CompletableFuture<Void>> original) {
        ((TrackedColumnSectionFence) column).refixes$markLoadStart(y, SEQUENCE.incrementAndGet());
        return original.call(tracker, x, y, z, playerRef, ref, chunkStore, column);
    }

    @Inject(method = "tryUnloadSection", at = @At("RETURN"))
    private void refixes$forgetUnloaded(
            int x,
            int y,
            int z,
            int chunkX,
            int chunkY,
            int chunkZ,
            com.hypixel.hytale.server.core.universe.PlayerRef playerRef,
            CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        Object column = trackedColumns.get(ChunkUtil.indexChunk(x, z));
        if (column != null) ((TrackedColumnSectionFence) column).refixes$forget(y);
    }
}
