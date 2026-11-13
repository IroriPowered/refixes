package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.ThrottleOwnership;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Holder;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Holder.class)
public abstract class MixinThrottleFreezeSave {

    @Unique
    private static final String refixes$FROZEN_ID = "Frozen";

    @ModifyReturnValue(
            method = "createComponentsMap(Lcom/hypixel/hytale/component/ComponentRegistry$Data;)Ljava/util/Map;",
            at = @At("RETURN"),
            require = 2,
            allow = 2)
    private <T> Map<String, Component<T>> refixes$dropThrottleFreeze(Map<String, Component<T>> components) {
        if (components.containsKey(ThrottleOwnership.MARKER_ID)) {
            components.remove(ThrottleOwnership.MARKER_ID);
            components.remove(refixes$FROZEN_ID);
        }
        return components;
    }
}
