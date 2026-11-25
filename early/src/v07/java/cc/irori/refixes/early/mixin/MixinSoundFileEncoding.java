package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.server.core.asset.common.SoundFileValidators;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@IfHytale("<0.8.0-pre.1")
@Mixin(SoundFileValidators.class)
public abstract class MixinSoundFileEncoding {

    @Inject(method = "getEncoding(I)Ljava/lang/String;", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private static void refixes$describeChannelCount(int channelCount, CallbackInfoReturnable<String> cir) {
        if (channelCount != 1 && channelCount != 2) cir.setReturnValue(channelCount + " channels");
    }
}
