package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.NotificationPacketAccess;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.CachedPacket;
import com.hypixel.hytale.protocol.packets.world.SetChunkEnvironments;
import com.hypixel.hytale.protocol.packets.world.SetChunkTintmap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldNotificationHandler;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EnvironmentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldNotificationHandler.class)
public abstract class MixinWorldNotificationHandler {

    @Shadow
    @Final
    private World world;

    @WrapOperation(
            method = "updateChunkTints",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$orderTints(
            CompletableFuture<CachedPacket<SetChunkTintmap>> future,
            Consumer<CachedPacket<SetChunkTintmap>> action,
            Operation<CompletableFuture<Void>> original,
            @Local BlockChunk chunk,
            @Local ArrayList<PlayerRef> players,
            @Local(argsOnly = true) long index) {
        long sequence = ((NotificationPacketAccess) chunk).refixes$beginNotification();
        return future.thenAcceptAsync(
                packet -> {
                    if (world.getChunkStore().getChunkComponent(index, BlockChunk.getComponentType()) != chunk) return;
                    if (!((NotificationPacketAccess) chunk).refixes$isCurrentNotification(sequence)) return;
                    refixes$removeDepartedPlayers(players);
                    action.accept(packet);
                },
                world);
    }

    @WrapOperation(
            method = "updateSectionEnvironments",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$orderEnvironments(
            CompletableFuture<CachedPacket<SetChunkEnvironments>> future,
            Consumer<CachedPacket<SetChunkEnvironments>> action,
            Operation<CompletableFuture<Void>> original,
            @Local EnvironmentSection section,
            @Local Ref<ChunkStore> sectionRef,
            @Local ArrayList<PlayerRef> players,
            @Local(argsOnly = true, ordinal = 0) int x,
            @Local(argsOnly = true, ordinal = 1) int y,
            @Local(argsOnly = true, ordinal = 2) int z) {
        long sequence = ((NotificationPacketAccess) section).refixes$beginNotification();
        return future.thenAcceptAsync(
                packet -> {
                    if (!sectionRef.isValid()) return;
                    if (world.getChunkStore().getChunkSectionReference(x, y, z) != sectionRef) return;
                    if (world.getChunkStore().getStore().getComponent(sectionRef, EnvironmentSection.getComponentType())
                            != section) return;
                    if (!((NotificationPacketAccess) section).refixes$isCurrentNotification(sequence)) return;
                    refixes$removeDepartedPlayers(players);
                    action.accept(packet);
                },
                world);
    }

    @Unique
    private void refixes$removeDepartedPlayers(ArrayList<PlayerRef> players) {
        players.removeIf(player -> {
            Ref<EntityStore> ref = player.getReference();
            return ref == null || ref.getStore() != world.getEntityStore().getStore() || !ref.isValid();
        });
    }
}
