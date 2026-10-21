package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.WorldTransferRecovery;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Location;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TeleportSystems.PlayerMoveSystem.class)
public abstract class MixinPlayerTeleport {
    @WrapMethod(method = "teleportToWorld")
    private void refixes$checkTransferOwnership(
            Ref<EntityStore> ref,
            Teleport teleport,
            CommandBuffer<EntityStore> commandBuffer,
            World target,
            Operation<Void> original) {
        PlayerRef player = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (player != null && WorldTransferRecovery.isTransferring(player)) {
            commandBuffer.removeComponent(ref, Teleport.getComponentType());
            if (teleport.getOnComplete() != null) {
                teleport.getOnComplete()
                        .completeExceptionally(new IllegalStateException("Player transfer is already pending"));
            }
            return;
        }
        original.call(ref, teleport, commandBuffer, target);
    }

    @WrapOperation(
            method = "teleportToWorld+ -> Ljava/util/function/Consumer;accept(Lcom/hypixel/hytale/component/Store;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/World;addPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/math/vector/Transform;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private static CompletableFuture<PlayerRef> refixes$recoverTeleport(
            World destination,
            PlayerRef player,
            Transform transform,
            Operation<CompletableFuture<PlayerRef>> original,
            @Local(argsOnly = true, ordinal = 0) Location origin,
            @Local(argsOnly = true) Store<EntityStore> source) {
        return WorldTransferRecovery.attach(
                destination,
                player,
                source.getExternalData().getWorld(),
                origin.toTransform(),
                () -> original.call(destination, player, transform));
    }
}
