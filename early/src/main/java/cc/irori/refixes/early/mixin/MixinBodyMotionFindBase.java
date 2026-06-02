package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.PathfindingBudget;
import com.hypixel.hytale.server.npc.corecomponents.movement.BodyMotionFindBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BodyMotionFindBase.class)
public class MixinBodyMotionFindBase {

    @ModifyExpressionValue(
            method =
                    "computeSteering(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/npc/role/Role;Lcom/hypixel/hytale/server/npc/sensorinfo/InfoProvider;DLcom/hypixel/hytale/server/npc/movement/Steering;Lcom/hypixel/hytale/component/ComponentAccessor;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/npc/corecomponents/movement/BodyMotionFindBase;shouldDeferPathComputation(Lcom/hypixel/hytale/server/npc/movement/controllers/MotionController;Lorg/joml/Vector3d;Lcom/hypixel/hytale/component/ComponentAccessor;)Z"))
    private boolean refixes$budgetNewPathSearch(boolean original, @Local(argsOnly = true) Role role) {
        if (original) {
            return true;
        }
        if (role.getCombatSupport().isExecutingAttack() || refixes$hasMarkedTarget(role)) {
            return false;
        }
        return !PathfindingBudget.tryConsume();
    }

    @Unique
    private static boolean refixes$hasMarkedTarget(Role role) {
        MarkedEntitySupport marked = role.getMarkedEntitySupport();
        int slots = marked.getMarkedEntitySlotCount();
        for (int i = 0; i < slots; i++) {
            if (marked.getMarkedEntityRef(i) != null) {
                return true;
            }
        }
        return false;
    }
}
