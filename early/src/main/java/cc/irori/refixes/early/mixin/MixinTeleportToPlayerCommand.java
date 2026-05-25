package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.teleport.commands.teleport.variant.TeleportToPlayerCommand;
import com.hypixel.hytale.builtin.teleport.components.TeleportHistory;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.universe.world.World;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Guards the deferred teleport-history append in the built-in /tp <player> command.
 *
 * Race condition: the lambda's preceding store.addComponent(ref, Teleport, ...) call
 * triggers a cross-world move when source and target worlds differ. The move nulls the
 * source ref's archetypeChunk before this lambda completes, so the subsequent
 * store.ensureAndGetComponent(ref, TeleportHistory.getComponentType()) NPEs on
 * archetypeChunk.__internal_getComponent(...). A HEAD ref.isValid() check is insufficient
 * because invalidation happens mid-lambda.
 *
 * If the lookup or append would fail, the player has already left this world; skipping the
 * history entry is the correct behavior (the teleport itself has already succeeded).
 */
@Mixin(TeleportToPlayerCommand.class)
public class MixinTeleportToPlayerCommand {

    @WrapOperation(
            method = "lambda$execute$1",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;ensureAndGetComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)Lcom/hypixel/hytale/component/Component;"))
    private static Component refixes$safeEnsureHistory(
            Store<?> store, Ref<?> ref, ComponentType<?, ?> type, Operation<Component> original) {
        if (ref == null || !ref.isValid()) {
            return null;
        }
        try {
            return original.call(store, ref, type);
        } catch (NullPointerException e) {
            return null;
        }
    }

    @WrapOperation(
            method = "lambda$execute$1",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/builtin/teleport/components/TeleportHistory;append(Lcom/hypixel/hytale/server/core/universe/world/World;Lorg/joml/Vector3d;Lcom/hypixel/hytale/math/vector/Rotation3f;Ljava/lang/String;)V"))
    private static void refixes$safeAppendHistory(
            TeleportHistory instance,
            World world,
            Vector3d pos,
            Rotation3f rotation,
            String reason,
            Operation<Void> original) {
        if (instance == null) {
            return;
        }
        original.call(instance, world, pos, rotation, reason);
    }
}
