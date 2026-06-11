package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CommandBuffer.class)
public class MixinCommandBuffer {

    @Redirect(
            method = "lambda$removeComponent$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;removeComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)V"))
    private static void refixes$safeRemoveComponent(
            Store<Object> instance, Ref<Object> ref, ComponentType<Object, ?> componentType) {
        instance.tryRemoveComponent(ref, componentType);
    }

    @Redirect(
            method = {"lambda$removeEntity$0", "lambda$removeEntity$1"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;removeEntity(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/Holder;Lcom/hypixel/hytale/component/RemoveReason;Ljava/lang/Throwable;)Lcom/hypixel/hytale/component/Holder;"))
    private static Holder<Object> refixes$safeRemoveEntity(
            Store<Object> instance, Ref<Object> ref, Holder<Object> holder, RemoveReason reason, Throwable source) {
        if (!ref.isValid()) {
            return holder;
        }
        return instance.removeEntity(ref, holder, reason);
    }
}
