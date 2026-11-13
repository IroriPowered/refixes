package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.time.Duration;
import java.time.Instant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TeleportSystems.MoveSystem.class)
public class MixinTransferCombatClock {

    @WrapOperation(
            method = "lambda$onComponentAdded$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/World;execute(Ljava/lang/Runnable;)V"),
            require = 1,
            allow = 1)
    private static void refixes$rebaseDamageClock(
            World target,
            Runnable arrival,
            Operation<Void> original,
            @Local(argsOnly = true) Store<EntityStore> store,
            @Local Holder<EntityStore> holder) {
        Instant sourceNow = store.getResource(TimeResource.getResourceType()).getNow();
        original.call(target, (Runnable) () -> {
            DamageDataComponent data =
                    holder == null ? null : holder.getComponent(DamageDataComponent.getComponentType());
            if (data != null) {
                Instant targetNow = target.getEntityStore()
                        .getStore()
                        .getResource(TimeResource.getResourceType())
                        .getNow();
                Duration shift = Duration.between(sourceNow, targetNow);
                if (!shift.isZero()) {
                    data.setLastCombatAction(refixes$shift(data.getLastCombatAction(), shift));
                    data.setLastDamageTime(refixes$shift(data.getLastDamageTime(), shift));
                    Instant charge = data.getLastChargeTime();
                    if (charge != null) {
                        data.setLastChargeTime(refixes$shift(charge, shift));
                    }
                }
            }
            arrival.run();
        });
    }

    private static Instant refixes$shift(Instant value, Duration shift) {
        return value.equals(Instant.MIN) ? value : value.plus(shift);
    }
}
