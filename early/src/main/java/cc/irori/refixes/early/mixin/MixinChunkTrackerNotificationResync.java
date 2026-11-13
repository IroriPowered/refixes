package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.NotificationPacketAccess;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkTracker.class)
public abstract class MixinChunkTrackerNotificationResync {

    @Inject(method = "_loadChunkAsync", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$captureTints(
            long index,
            PlayerRef player,
            Ref<ChunkStore> ref,
            ChunkStore store,
            @Coerce Object column,
            CallbackInfoReturnable<CompletableFuture<Void>> cir,
            @Share("refixes$notificationRefresh") LocalRef<Runnable> refresh) {
        BlockChunk source = store.getStore().getComponent(ref, BlockChunk.getComponentType());
        if (source != null) {
            refresh.set(refixes$afterLoad(
                    player,
                    store.getWorld(),
                    ref,
                    source,
                    ChunkUtil.xOfChunkIndex(index),
                    -1,
                    ChunkUtil.zOfChunkIndex(index)));
        }
    }

    @Inject(method = "_loadSectionAsync", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$captureEnvironments(
            int x,
            int y,
            int z,
            PlayerRef player,
            Ref<ChunkStore> ref,
            ChunkStore store,
            @Coerce Object column,
            CallbackInfoReturnable<CompletableFuture<Void>> cir,
            @Share("refixes$notificationRefresh") LocalRef<Runnable> refresh) {
        EnvironmentSection source = store.getStore().getComponent(ref, EnvironmentSection.getComponentType());
        if (source != null) refresh.set(refixes$afterLoad(player, store.getWorld(), ref, source, x, y, z));
    }

    @WrapOperation(
            method = {"_loadChunkAsync", "_loadSectionAsync"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"),
            require = 2,
            allow = 2)
    private CompletableFuture<Void> refixes$refreshAfterLoad(
            CompletableFuture<Void> future,
            Consumer<Void> action,
            Operation<CompletableFuture<Void>> original,
            @Share("refixes$notificationRefresh") LocalRef<Runnable> refresh) {
        CompletableFuture<Void> completion = original.call(future, action);
        if (refresh.get() != null) completion.thenRun(refresh.get());
        return completion;
    }

    @Unique
    private static Runnable refixes$afterLoad(
            PlayerRef player, World world, Ref<ChunkStore> ref, Object source, int x, int y, int z) {
        NotificationPacketAccess access = (NotificationPacketAccess) source;
        long sequence = access.refixes$getNotificationSequence();
        return () -> {
            if (!access.refixes$isCurrentNotification(sequence)) {
                world.execute(() -> refixes$refresh(player, world, ref, source, x, y, z));
            }
        };
    }

    @Unique
    private static void refixes$refresh(
            PlayerRef player, World world, Ref<ChunkStore> ref, Object source, int x, int y, int z) {
        if (!refixes$currentTarget(player, world, ref, source, x, y, z)) return;
        NotificationPacketAccess access = (NotificationPacketAccess) source;
        long sequence = access.refixes$getNotificationSequence();
        CompletableFuture<? extends ToClientPacket> future = source instanceof BlockChunk chunk
                ? chunk.getCachedTintsPacket(x, z)
                : ((EnvironmentSection) source).getCachedPacket(x, y, z);
        future.thenAcceptAsync(
                packet -> {
                    if (packet != null
                            && access.refixes$isCurrentNotification(sequence)
                            && refixes$currentTarget(player, world, ref, source, x, y, z)) {
                        player.getPacketHandler().write(packet);
                    }
                },
                world);
    }

    @Unique
    private static boolean refixes$currentTarget(
            PlayerRef player, World world, Ref<ChunkStore> ref, Object source, int x, int y, int z) {
        Ref<EntityStore> playerRef = player.getReference();
        if (playerRef == null || playerRef.getStore() != world.getEntityStore().getStore() || !playerRef.isValid())
            return false;
        if (!ref.isValid()) return false;
        ChunkStore store = world.getChunkStore();
        if (source instanceof BlockChunk) {
            long index = ChunkUtil.indexChunk(x, z);
            return store.getChunkReference(index) == ref
                    && store.getStore().getComponent(ref, BlockChunk.getComponentType()) == source
                    && player.getChunkTracker().isLoaded(index);
        }
        return store.getChunkSectionReference(x, y, z) == ref
                && store.getStore().getComponent(ref, EnvironmentSection.getComponentType()) == source
                && player.getChunkTracker().isLoaded(x, y, z);
    }
}
