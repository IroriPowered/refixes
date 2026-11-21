package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.asset.type.model.config.ModelParticle;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelParticle.class)
public abstract class MixinModelParticleScale {

    @Shadow
    protected Vector3f positionOffset;

    @Inject(
            method = "scale(F)Lcom/hypixel/hytale/server/core/asset/type/model/config/ModelParticle;",
            at = @At("HEAD"),
            require = 1,
            allow = 1)
    private void refixes$copyOffsetBeforeScaling(float scale, CallbackInfoReturnable<ModelParticle> cir) {
        if (positionOffset != null) {
            positionOffset = new Vector3f(positionOffset);
        }
    }
}
