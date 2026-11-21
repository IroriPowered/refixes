package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.FluidLightVersion;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidSection.class)
public class MixinFluidSectionLightNeutral implements FluidLightVersion {

    @Unique
    private volatile int refixes$fluidVersion;

    @Inject(method = "setFluidRaw(II)Z", at = @At("RETURN"), require = 1)
    private void refixes$countFluidChange(int index, int fluidId, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) refixes$fluidVersion++;
    }

    @Inject(method = "deserialize([BLcom/hypixel/hytale/codec/ExtraInfo;)V", at = @At("RETURN"), require = 1)
    private void refixes$countFluidLoad(byte[] bytes, ExtraInfo extraInfo, CallbackInfo ci) {
        refixes$fluidVersion++;
    }

    @Override
    public int refixes$fluidVersion() {
        return refixes$fluidVersion;
    }
}
