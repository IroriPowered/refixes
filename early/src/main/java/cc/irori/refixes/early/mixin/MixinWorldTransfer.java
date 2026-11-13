package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.EntityAdmission;
import cc.irori.refixes.early.util.WorldPolicyTransfer;
import cc.irori.refixes.early.util.WorldTransferRecovery;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SpawnUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class MixinWorldTransfer implements EntityAdmission {
    @Unique
    private final Object refixes$admission = new Object();

    @Unique
    private final Object refixes$queue = new Object();

    @Unique
    private volatile boolean refixes$closing;

    @Inject(
            method = "stopIndividualWorldAsync(Ljava/util/Map;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"))
    private void refixes$cancelTransfersOnStop(CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        refixes$cancelTransfers();
    }

    @Inject(
            method = {"stopIndividualWorld(Ljava/util/Map;)V", "onShutdown"},
            at = @At("HEAD"))
    private void refixes$cancelTransfersOnShutdown(CallbackInfo ci) {
        refixes$cancelTransfers();
    }

    @Unique
    private void refixes$cancelTransfers() {
        synchronized (refixes$admission) {
            synchronized (refixes$queue) {
                refixes$closing = true;
            }
        }
        WorldPolicyTransfer.closeWorld((World) (Object) this);
    }

    @WrapMethod(
            method =
                    "addPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/math/vector/Transform;Ljava/lang/Boolean;Ljava/lang/Boolean;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<PlayerRef> refixes$transferPlayer(
            PlayerRef playerRef,
            Transform transform,
            Boolean clearWorldOverride,
            Boolean fadeInOutOverride,
            Operation<CompletableFuture<PlayerRef>> original) {
        Ref<EntityStore> initialReference = playerRef.getReference();
        if (refixes$closing) {
            if (initialReference == null && !WorldTransferRecovery.isTransferring(playerRef)) {
                if (!((World) (Object) this).isAlive()
                        || !playerRef.getPacketHandler().stillActive()) {
                    return original.call(playerRef, transform, clearWorldOverride, fadeInOutOverride);
                }
                return WorldTransferRecovery.admit(
                        (World) (Object) this,
                        playerRef,
                        () -> CompletableFuture.failedFuture(WorldTransferRecovery.closedDuringAdmission()));
            }
            return CompletableFuture.failedFuture(refixes$closed());
        }
        if (initialReference == null) {
            return WorldTransferRecovery.admit(
                    (World) (Object) this,
                    playerRef,
                    () -> original.call(playerRef, transform, clearWorldOverride, fadeInOutOverride));
        }
        return WorldPolicyTransfer.awaitRemovalAndAttach(
                (World) (Object) this,
                playerRef,
                initialReference,
                () -> WorldTransferRecovery.admit(
                        (World) (Object) this,
                        playerRef,
                        () -> original.call(playerRef, transform, clearWorldOverride, fadeInOutOverride)));
    }

    @WrapOperation(
            method =
                    "addPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/math/vector/Transform;Ljava/lang/Boolean;Ljava/lang/Boolean;)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/World;addPositionedPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/component/Holder;Lcom/hypixel/hytale/server/core/io/PacketHandler;Lcom/hypixel/hytale/server/core/modules/entity/component/TransformComponent;Lcom/hypixel/hytale/math/vector/Transform;Ljava/lang/Boolean;Ljava/lang/Boolean;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<PlayerRef> refixes$positionWithoutTransform(
            World world,
            PlayerRef player,
            Holder<EntityStore> holder,
            PacketHandler handler,
            TransformComponent current,
            Transform transform,
            Boolean clearWorld,
            Boolean fadeInOut,
            Operation<CompletableFuture<PlayerRef>> original) {
        if (current == null && transform != null && !refixes$closing) {
            current = SpawnUtil.applyFirstSpawnTransform(holder, transform);
        }
        return original.call(world, player, holder, handler, current, transform, clearWorld, fadeInOut);
    }

    @Inject(method = "addPositionedPlayer", at = @At("HEAD"), cancellable = true)
    private void refixes$checkPositionedAdmission(
            PlayerRef player,
            Holder<EntityStore> holder,
            PacketHandler handler,
            TransformComponent currentTransform,
            Transform transform,
            Boolean clearWorld,
            Boolean fadeInOut,
            CallbackInfoReturnable<CompletableFuture<PlayerRef>> cir) {
        if (refixes$closing) {
            if (!WorldTransferRecovery.manages((World) (Object) this, player)
                    && !WorldTransferRecovery.hasOtherOwner((World) (Object) this, player)) {
                handler.disconnect(Message.translation("server.general.disconnect.exceptionJoiningWorld"));
            }
            cir.setReturnValue(CompletableFuture.failedFuture(refixes$closed()));
        }
    }

    @WrapMethod(method = "onFinishPlayerJoining")
    private PlayerRef refixes$finishAdmission(
            Player player,
            PlayerRef playerRef,
            PacketHandler packetHandler,
            Message joinMessage,
            boolean broadcast,
            Operation<PlayerRef> original) {
        synchronized (refixes$admission) {
            if (refixes$closing) throw WorldTransferRecovery.closedDuringAdmission();
            WorldTransferRecovery.checkAdmission((World) (Object) this, playerRef);
            return original.call(player, playerRef, packetHandler, joinMessage, broadcast);
        }
    }

    @WrapOperation(
            method = "addPositionedPlayer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<PlayerRef> refixes$queueAdmission(
            CompletableFuture<Void> ready,
            Function<Void, PlayerRef> finish,
            Executor executor,
            Operation<CompletableFuture<PlayerRef>> original) {
        Executor admission = task -> {
            synchronized (refixes$queue) {
                if (refixes$closing) throw WorldTransferRecovery.closedDuringAdmission();
                executor.execute(task);
            }
        };
        return original.call(ready, finish, admission);
    }

    @WrapOperation(
            method = "addPositionedPlayer",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;exceptionally(Ljava/util/function/Function;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<PlayerRef> refixes$recoverManagedTransfer(
            CompletableFuture<PlayerRef> joined,
            Function<Throwable, PlayerRef> failure,
            Operation<CompletableFuture<PlayerRef>> original,
            @Local(argsOnly = true) PlayerRef player) {
        Function<Throwable, PlayerRef> handler = error -> {
            if (WorldTransferRecovery.manages((World) (Object) this, player)
                    || WorldTransferRecovery.hasOtherOwner((World) (Object) this, player)
                    || WorldTransferRecovery.isSuperseded(error)
                    || WorldTransferRecovery.isClosedDuringAdmission(error)) throw new CompletionException(error);
            return failure.apply(error);
        };
        return original.call(joined, handler);
    }

    @Override
    public boolean refixes$acceptsEntities() {
        return !refixes$closing;
    }

    @Override
    public boolean refixes$admitEntity(Runnable offer) {
        synchronized (refixes$queue) {
            if (refixes$closing) return false;
            try {
                offer.run();
            } catch (SkipSentryException rejected) {
                if (!(rejected.getCause() instanceof IllegalThreadStateException)) throw rejected;
                return false;
            }
            return true;
        }
    }

    @Unique
    private IllegalStateException refixes$closed() {
        return new IllegalStateException("Destination world is shutting down");
    }
}
