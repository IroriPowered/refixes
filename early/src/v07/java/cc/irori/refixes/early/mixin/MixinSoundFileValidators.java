package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.SoundHeaderInfo;
import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache.OggVorbisInfo;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(
        targets = {
            "com.hypixel.hytale.server.core.asset.common.SoundFileValidators$ChannelValidator",
            "com.hypixel.hytale.server.core.asset.common.SoundFileValidators$SampleRateValidator"
        })
public abstract class MixinSoundFileValidators {

    @WrapOperation(
            method = "accept(Ljava/lang/String;Lcom/hypixel/hytale/codec/validation/ValidationResults;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/asset/common/OggVorbisInfoCache;getNow(Ljava/lang/String;)Lcom/hypixel/hytale/server/core/asset/common/OggVorbisInfoCache$OggVorbisInfo;"))
    private OggVorbisInfo refixes$headerInfo(String name, Operation<OggVorbisInfo> original) {
        return SoundHeaderInfo.getNow(name, original);
    }
}
