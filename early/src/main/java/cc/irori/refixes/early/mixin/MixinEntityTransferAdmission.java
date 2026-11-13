package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.EntityAdmission;
import cc.irori.refixes.early.util.CubicTransfer;
import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.WorldTransferRecovery;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TeleportSystems.MoveSystem.class)
public class MixinEntityTransferAdmission {

    @Unique
    private static final ThreadLocal<Origin> refixes$origin = new ThreadLocal<>();

    @Unique
    private record Origin(Vector3d position, Rotation3f rotation, Rotation3f head) {}

    @Inject(
            method =
                    "onComponentAdded(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/modules/entity/teleport/Teleport;Lcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/component/CommandBuffer;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void refixes$keepOrigin(
            Ref<EntityStore> ref,
            Teleport teleport,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci,
            @Share("origin") LocalRef<Origin> origin) {
        World target = teleport.getWorld();
        World source = store.getExternalData().getWorld();
        if (target != null
                && !target.equals(source)
                && target instanceof EntityAdmission admission
                && !admission.refixes$acceptsEntities()) {
            commandBuffer.removeComponent(ref, Teleport.getComponentType());
            CompletableFuture<Void> future = teleport.getOnComplete();
            if (future != null) future.completeExceptionally(WorldTransferRecovery.closedDuringAdmission());
            ci.cancel();
            return;
        }
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        HeadRotation head = commandBuffer.getComponent(ref, HeadRotation.getComponentType());
        origin.set(new Origin(
                new Vector3d(transform.getPosition()),
                new Rotation3f(transform.getRotation()),
                head == null ? null : new Rotation3f(head.getRotation())));
    }

    @WrapOperation(
            method =
                    "onComponentAdded(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/modules/entity/teleport/Teleport;Lcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/component/CommandBuffer;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/hytale/component/CommandBuffer;run(Ljava/util/function/Consumer;)V"),
            require = 1,
            allow = 1)
    private void refixes$carryOrigin(
            CommandBuffer<EntityStore> commandBuffer,
            Consumer<Store<EntityStore>> move,
            Operation<Void> original,
            @Share("origin") LocalRef<Origin> origin) {
        Origin from = origin.get();
        if (from == null) {
            original.call(commandBuffer, move);
            return;
        }
        original.call(commandBuffer, (Consumer<Store<EntityStore>>) store -> {
            Origin previous = refixes$origin.get();
            refixes$origin.set(from);
            try {
                move.accept(store);
            } finally {
                if (previous == null) refixes$origin.remove();
                else refixes$origin.set(previous);
            }
        });
    }

    @WrapOperation(
            method = "lambda$onComponentAdded$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/World;execute(Ljava/lang/Runnable;)V"),
            require = 1,
            allow = 1)
    private static void refixes$admitArrival(
            World target,
            Runnable arrival,
            Operation<Void> original,
            @Local(argsOnly = true) CompletableFuture<Void> future,
            @Local(argsOnly = true) Store<EntityStore> store,
            @Local Holder<EntityStore> holder) {
        Origin from = refixes$origin.get();
        if (from == null || holder == null || !(target instanceof EntityAdmission admission)) {
            original.call(target, arrival);
            return;
        }
        if (admission.refixes$admitEntity(() -> original.call(target, arrival))) return;
        refixes$restore(store, target, holder, from, future);
    }

    @Unique
    private static void refixes$restore(
            Store<EntityStore> store,
            World target,
            Holder<EntityStore> holder,
            Origin from,
            CompletableFuture<Void> future) {
        World source = store.getExternalData().getWorld();
        IllegalStateException failure = WorldTransferRecovery.closedDuringAdmission();
        UUIDComponent uuid = holder.getComponent(UUIDComponent.getComponentType());
        TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
        if (transform != null) {
            transform.setPosition(from.position());
            transform.setRotation(from.rotation());
        }
        HeadRotation head = holder.getComponent(HeadRotation.getComponentType());
        if (head != null && from.head() != null) head.setRotation(from.head());
        UUID id = uuid == null ? null : uuid.getUuid();
        Ref<EntityStore> restored;
        try {
            restored = store.isShutdown() ? null : store.addEntity(holder, AddReason.LOAD);
        } catch (RuntimeException error) {
            failure.addSuppressed(error);
            if (future != null) future.completeExceptionally(failure);
            throw error;
        }
        if (restored != null && id != null) CubicTransfer.restored(source, id);
        if (restored == null) {
            Logs.logger()
                    .atSevere()
                    .withCause(failure)
                    .log(
                            "Entity %s could not move to world %s and could not return to world %s",
                            id, target.getName(), source.getName());
        } else {
            Logs.logger()
                    .atWarning()
                    .log(
                            "Entity %s stayed in world %s because world %s is shutting down",
                            id, source.getName(), target.getName());
        }
        if (future != null) future.completeExceptionally(failure);
    }
}
