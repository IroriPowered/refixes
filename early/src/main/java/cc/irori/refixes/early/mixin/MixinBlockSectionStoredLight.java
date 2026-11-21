package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.BlockSectionStoredLight;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockSection.class)
public abstract class MixinBlockSectionStoredLight implements BlockSectionStoredLight {

    @Unique
    private volatile boolean refixes$storedUnlit;

    @Shadow
    public abstract boolean hasLocalLight();

    @Shadow
    public abstract boolean hasGlobalLight();

    @Inject(method = "serialize(Lcom/hypixel/hytale/codec/ExtraInfo;)[B", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$checkLightBeforeWrite(
            ExtraInfo extraInfo, CallbackInfoReturnable<byte[]> cir, @Share("refixes$unlit") LocalBooleanRef unlit) {
        unlit.set(!hasLocalLight() || !hasGlobalLight());
    }

    @Inject(method = "serialize(Lcom/hypixel/hytale/codec/ExtraInfo;)[B", at = @At("RETURN"), require = 1, allow = 1)
    private void refixes$checkLightAfterWrite(
            ExtraInfo extraInfo, CallbackInfoReturnable<byte[]> cir, @Share("refixes$unlit") LocalBooleanRef unlit) {
        refixes$storedUnlit = unlit.get() || !hasLocalLight() || !hasGlobalLight();
    }

    @Inject(method = "deserialize([BLcom/hypixel/hytale/codec/ExtraInfo;)V", at = @At("RETURN"), require = 1, allow = 1)
    private void refixes$checkLoadedLight(byte[] bytes, ExtraInfo extraInfo, CallbackInfo ci) {
        refixes$storedUnlit = !hasLocalLight() || !hasGlobalLight();
    }

    @Override
    public boolean refixes$storedUnlit() {
        return refixes$storedUnlit;
    }
}
