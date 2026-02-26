package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MotionControllerBase.class)
public class MixinMotionControllerBase {

    @Shadow
    protected Vector3d translation;

    @Inject(
            method = "steer0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/npc/movement/controllers/MotionControllerBase;executeMove(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/npc/role/Role;DLcom/hypixel/hytale/math/vector/Vector3d;Lcom/hypixel/hytale/component/ComponentAccessor;)D"))
    private void refixes$guardNaNTranslation(CallbackInfoReturnable<Double> cir) {
        if (!Double.isFinite(translation.x) || !Double.isFinite(translation.y) || !Double.isFinite(translation.z)) {
            translation.assign(0.0);
        }
    }
}
