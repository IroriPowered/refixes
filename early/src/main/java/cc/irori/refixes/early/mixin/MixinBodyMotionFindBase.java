package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.PathfindingBudget;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.corecomponents.movement.BodyMotionFindBase;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BodyMotionFindBase.class)
public class MixinBodyMotionFindBase {

    @Shadow
    @Final
    protected int nodesPerTick;

    @ModifyExpressionValue(
            method =
                    "computeSteering(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/npc/instructions/ExecutionSupport;Lcom/hypixel/hytale/server/npc/sensorinfo/InfoProvider;DLcom/hypixel/hytale/server/npc/movement/Steering;Lcom/hypixel/hytale/component/ComponentAccessor;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/npc/corecomponents/movement/BodyMotionFindBase;shouldDeferPathComputation(Lcom/hypixel/hytale/server/npc/movement/controllers/MotionController;Lorg/joml/Vector3d;Lcom/hypixel/hytale/component/ComponentAccessor;)Z"))
    private boolean refixes$budgetNewPathSearch(
            boolean original,
            @Local(argsOnly = true) ExecutionSupport executionSupport,
            @Local(argsOnly = true) ComponentAccessor<EntityStore> componentAccessor) {
        if (original) {
            return true;
        }
        if (refixes$isBudgetExempt(executionSupport)) {
            return false;
        }
        return !PathfindingBudget.tryConsume(componentAccessor);
    }

    @WrapOperation(
            method =
                    "computeSteering(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/npc/instructions/ExecutionSupport;Lcom/hypixel/hytale/server/npc/sensorinfo/InfoProvider;DLcom/hypixel/hytale/server/npc/movement/Steering;Lcom/hypixel/hytale/component/ComponentAccessor;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/npc/corecomponents/movement/BodyMotionFindBase;startPathFinder(Lcom/hypixel/hytale/component/Ref;Lorg/joml/Vector3d;Lcom/hypixel/hytale/server/npc/instructions/ExecutionSupport;Lcom/hypixel/hytale/server/npc/movement/controllers/MotionController;Lcom/hypixel/hytale/component/ComponentAccessor;)Z",
                            ordinal = 1))
    private boolean refixes$budgetEarlyStartPathSearch(
            BodyMotionFindBase<?> instance,
            Ref<EntityStore> ref,
            Vector3d position,
            ExecutionSupport executionSupport,
            MotionController activeMotionController,
            ComponentAccessor<EntityStore> componentAccessor,
            Operation<Boolean> original) {
        if (refixes$isBudgetExempt(executionSupport) || PathfindingBudget.tryConsume(componentAccessor)) {
            return original.call(instance, ref, position, executionSupport, activeMotionController, componentAccessor);
        }
        return false;
    }

    @WrapOperation(
            method =
                    "computeSteering(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/npc/instructions/ExecutionSupport;Lcom/hypixel/hytale/server/npc/sensorinfo/InfoProvider;DLcom/hypixel/hytale/server/npc/movement/Steering;Lcom/hypixel/hytale/component/ComponentAccessor;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/npc/corecomponents/movement/BodyMotionFindBase;continuePathFinder(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/npc/movement/controllers/MotionController;Lcom/hypixel/hytale/component/ComponentAccessor;)Z"))
    private boolean refixes$budgetPathSearchContinuation(
            BodyMotionFindBase<?> instance,
            Ref<EntityStore> ref,
            MotionController activeMotionController,
            ComponentAccessor<EntityStore> componentAccessor,
            Operation<Boolean> original,
            @Local(argsOnly = true) ExecutionSupport executionSupport) {
        if (refixes$isBudgetExempt(executionSupport)
                || PathfindingBudget.tryConsumeNodes(componentAccessor, this.nodesPerTick)) {
            return original.call(instance, ref, activeMotionController, componentAccessor);
        }
        return true;
    }

    @Unique
    private static boolean refixes$isBudgetExempt(ExecutionSupport executionSupport) {
        return executionSupport.getCombatSupport().isExecutingAttack() || refixes$hasMarkedTarget(executionSupport);
    }

    @Unique
    private static boolean refixes$hasMarkedTarget(ExecutionSupport executionSupport) {
        MarkedEntitySupport marked = executionSupport.getMarkedEntitySupport();
        int slots = marked.getMarkedEntitySlotCount();
        for (int i = 0; i < slots; i++) {
            if (marked.getMarkedEntityRef(i) != null) {
                return true;
            }
        }
        return false;
    }
}
