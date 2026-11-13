package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.EntityAdmission;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Universe.class)
public abstract class MixinUniverseResetOwnership {
    @WrapOperation(
            method =
                    "resetPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/component/Holder;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/math/vector/Transform;)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/World;execute(Ljava/lang/Runnable;)V"),
            require = 1,
            allow = 1)
    private void refixes$refuseStaleResetOwner(
            World oldWorld,
            Runnable detach,
            Operation<Void> original,
            @Local(argsOnly = true) PlayerRef player,
            @Local(argsOnly = true) World target,
            @Local CompletableFuture<Void> detached) {
        Ref<EntityStore> expected = player.getReference();
        original.call(oldWorld, (Runnable) () -> {
            try {
                if (expected == null
                        || player.getReference() != expected
                        || !expected.isValid()
                        || expected.getStore() != oldWorld.getEntityStore().getStore()
                        || !((EntityAdmission) (target != null ? target : oldWorld)).refixes$acceptsEntities()) {
                    detached.completeExceptionally(new IllegalStateException("Player reset ownership changed"));
                    return;
                }
                detach.run();
            } catch (Throwable t) {
                detached.completeExceptionally(t);
                throw t;
            }
        });
    }

    @Inject(
            method =
                    "resetPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/component/Holder;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/math/vector/Transform;)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/Universe;getLogger()Lcom/hypixel/hytale/logger/HytaleLogger;"),
            cancellable = true,
            require = 1,
            allow = 1)
    private void refixes$refuseUnavailableDestination(
            PlayerRef player,
            Holder<EntityStore> holder,
            World target,
            Transform transform,
            CallbackInfoReturnable<CompletableFuture<PlayerRef>> cir,
            @Local Player oldPlayer) {
        World destination = target != null ? target : oldPlayer.getWorld();
        if (destination == null) {
            cir.setReturnValue(
                    CompletableFuture.failedFuture(new IllegalStateException("Player reset has no destination world")));
        } else if (!destination.isAlive()) {
            cir.setReturnValue(CompletableFuture.failedFuture(
                    new SkipSentryException(new IllegalStateException("This world has already been shutdown!"))));
        } else if (!((EntityAdmission) destination).refixes$acceptsEntities()) {
            cir.setReturnValue(
                    CompletableFuture.failedFuture(new IllegalStateException("Destination world is shutting down")));
        }
    }

    @WrapOperation(
            method = "lambda$resetPlayer$1",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/Universe;resetPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/component/Holder;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<PlayerRef> refixes$resetOnOwner(
            Universe universe,
            PlayerRef player,
            Holder<EntityStore> holder,
            Operation<CompletableFuture<PlayerRef>> original) {
        Ref<EntityStore> expected = player.getReference();
        if (expected == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Player reset ownership changed"));
        }
        CompletableFuture<PlayerRef> result = new CompletableFuture<>();
        try {
            World owner = expected.getStore().getExternalData().getWorld();
            Runnable reset = () -> {
                try {
                    Player state = player.getReference() != expected || !expected.isValid()
                            ? null
                            : expected.getStore().getComponent(expected, Player.getComponentType());
                    if (state == null
                            || state.getWorld() != owner
                            || !((EntityAdmission) owner).refixes$acceptsEntities()) {
                        result.completeExceptionally(new IllegalStateException("Player reset ownership changed"));
                        return;
                    }
                    CompletionStage<PlayerRef> reply = original.call(universe, player, holder);
                    reply.whenComplete((value, error) -> {
                        if (error != null) result.completeExceptionally(error);
                        else result.complete(value);
                    });
                } catch (Throwable t) {
                    result.completeExceptionally(t);
                }
            };
            if (!((EntityAdmission) owner).refixes$admitEntity(() -> owner.execute(reset))) {
                result.completeExceptionally(new IllegalStateException("Player reset ownership changed"));
            }
        } catch (Throwable t) {
            result.completeExceptionally(t);
        }
        return result;
    }
}
