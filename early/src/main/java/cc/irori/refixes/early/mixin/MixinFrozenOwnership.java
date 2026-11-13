package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.ThrottleOwnership;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Store.class)
public class MixinFrozenOwnership {

    @Inject(
            method = "ensureComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;takeCommandBuffer()Lcom/hypixel/hytale/component/CommandBuffer;"),
            require = 1)
    private void refixes$claimEnsure(Ref<?> ref, ComponentType<?, ?> type, CallbackInfo ci) {
        refixes$claim(ref, type);
    }

    @Inject(
            method =
                    "ensureAndGetComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)Lcom/hypixel/hytale/component/Component;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;takeCommandBuffer()Lcom/hypixel/hytale/component/CommandBuffer;"),
            require = 1)
    private void refixes$claimEnsureAndGet(
            Ref<?> ref, ComponentType<?, ?> type, CallbackInfoReturnable<Component<?>> cir) {
        refixes$claim(ref, type);
    }

    @Inject(
            method =
                    "putComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;Lcom/hypixel/hytale/component/Component;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;takeCommandBuffer()Lcom/hypixel/hytale/component/CommandBuffer;"),
            require = 1)
    private void refixes$claimPut(Ref<?> ref, ComponentType<?, ?> type, Component<?> component, CallbackInfo ci) {
        refixes$claim(ref, type);
    }

    @SuppressWarnings("unchecked")
    private void refixes$claim(Ref<?> ref, ComponentType<?, ?> type) {
        if (type.getTypeClass() != Frozen.class || type != Frozen.getComponentType()) {
            return;
        }
        Store<EntityStore> store = (Store<EntityStore>) (Object) this;
        Ref<EntityStore> entityRef = (Ref<EntityStore>) ref;
        if (entityRef.isValid()
                && entityRef.getStore() == store
                && store.getArchetype(entityRef).contains(Frozen.getComponentType())) {
            ThrottleOwnership.claim(store, entityRef, Frozen.getComponentType());
        }
    }
}
