package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CommandBuffer.class)
public class MixinCommandBuffer {

    @Unique
    private static final Set<Class<?>> refixes$reportedMissing = ConcurrentHashMap.newKeySet();

    @Redirect(
            method =
                    "removeComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)V -> Ljava/util/function/Consumer;accept(Lcom/hypixel/hytale/component/Store;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;removeComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)V"))
    private static void refixes$safeRemoveComponent(
            Store<Object> instance, Ref<Object> ref, ComponentType<Object, ?> componentType) {
        if (!ref.isValid() || ref.getStore() != instance) {
            return;
        }
        if (!instance.getArchetype(ref).contains(componentType)
                && refixes$reportedMissing.add(componentType.getTypeClass())) {
            Logs.logger()
                    .atWarning()
                    .log(
                            "Ignored a queued removeComponent of %s from an entity that no longer has it"
                                    + " (reported once per component type)",
                            componentType.getTypeClass().getName());
        }
        instance.tryRemoveComponent(ref, componentType);
    }

    @WrapOperation(
            method =
                    "tryRemoveComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)V -> Ljava/util/function/Consumer;accept(Lcom/hypixel/hytale/component/Store;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;tryRemoveComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)V"),
            require = 1)
    private static void refixes$safeTryRemoveComponent(
            Store<Object> instance, Ref<Object> ref, ComponentType<Object, ?> componentType, Operation<Void> original) {
        if (!ref.isValid() || ref.getStore() != instance) {
            return;
        }
        original.call(instance, ref, componentType);
    }

    @WrapOperation(
            method = {
                "removeEntity+ -> Ljava/util/function/Consumer;accept(Lcom/hypixel/hytale/component/Store;)V",
                "tryRemoveEntity(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/RemoveReason;)V -> Ljava/util/function/Consumer;accept(Lcom/hypixel/hytale/component/Store;)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;removeEntity(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/Holder;Lcom/hypixel/hytale/component/RemoveReason;Ljava/lang/Throwable;)Lcom/hypixel/hytale/component/Holder;"))
    private static Holder<Object> refixes$safeRemoveEntity(
            Store<Object> instance,
            Ref<Object> ref,
            Holder<Object> holder,
            RemoveReason reason,
            Throwable source,
            Operation<Holder<Object>> original) {
        if (!ref.isValid() || ref.getStore() != instance) {
            return holder;
        }
        return original.call(instance, ref, holder, reason, source);
    }
}
