package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ComponentType;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(Archetype.class)
public class MixinSerializableArchetype {

    @WrapOperation(
            method = "getSerializableArchetype",
            at = @At(value = "NEW", target = "com/hypixel/hytale/component/Archetype"),
            require = 1,
            allow = 1)
    private Archetype<?> refixes$firstSerializableIndex(
            int minIndex, int count, ComponentType<?, ?>[] componentTypes, Operation<Archetype<?>> original) {
        int index = minIndex;
        while (index < componentTypes.length && componentTypes[index] == null) {
            index++;
        }
        return original.call(index, count, componentTypes);
    }
}
