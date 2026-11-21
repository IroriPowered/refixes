package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.PatchCounters;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MotionControllerBase.class)
public abstract class MixinDefaultMovementResistance {

    @Unique
    private static final VelocityConfig REFIXES$DEFAULT_RESISTANCE = playerDefaultMovementResistance();

    @Shadow
    private static VelocityConfig playerDefaultMovementResistance() {
        throw new AssertionError();
    }

    @Redirect(
            method = {
                "addVelocity(Lorg/joml/Vector3d;Lcom/hypixel/hytale/server/core/modules/splitvelocity/VelocityConfig;F)V",
                "setVelocity(Lorg/joml/Vector3dc;Lcom/hypixel/hytale/server/core/modules/splitvelocity/VelocityConfig;ZF)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/npc/movement/controllers/MotionControllerBase;playerDefaultMovementResistance()Lcom/hypixel/hytale/server/core/modules/splitvelocity/VelocityConfig;"),
            require = 2,
            allow = 2)
    private VelocityConfig refixes$sharedDefaultResistance() {
        if (PatchCounters.ENABLED) {
            PatchCounters.DEFAULT_MOVEMENT_RESISTANCE.countReached();
            PatchCounters.DEFAULT_MOVEMENT_RESISTANCE.countEffect();
        }
        return REFIXES$DEFAULT_RESISTANCE;
    }
}
