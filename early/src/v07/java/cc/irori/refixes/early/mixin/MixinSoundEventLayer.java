package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.SoundHeaderInfo;
import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache.OggVorbisInfo;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEventLayer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(SoundEventLayer.class)
public abstract class MixinSoundEventLayer {

    @WrapOperation(
            method = "lambda$static$18",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/asset/common/OggVorbisInfoCache;getNow(Ljava/lang/String;)Lcom/hypixel/hytale/server/core/asset/common/OggVorbisInfoCache$OggVorbisInfo;"))
    private static OggVorbisInfo refixes$headerChannels(String name, Operation<OggVorbisInfo> original) {
        return SoundHeaderInfo.getNow(name, original);
    }
}
