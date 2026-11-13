package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.instances.interactions.TeleportInstanceInteraction;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TeleportInstanceInteraction.class)
public class MixinTeleportInstanceInteraction {

    @WrapOperation(
            method = "makeReturnPoint",
            at = @At(value = "INVOKE", target = "Lorg/joml/Vector3d;add(Lorg/joml/Vector3dc;)Lorg/joml/Vector3d;"),
            require = 1,
            allow = 1)
    private Vector3d refixes$applyOptionalOffset(Vector3d position, Vector3dc offset, Operation<Vector3d> original) {
        return offset == null ? position : original.call(position, offset);
    }
}
