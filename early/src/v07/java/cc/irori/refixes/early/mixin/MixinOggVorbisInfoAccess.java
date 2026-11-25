package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache.OggVorbisInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@IfHytale("<0.8.0-pre.1")
@Mixin(OggVorbisInfo.class)
public interface MixinOggVorbisInfoAccess {

    @Invoker("<init>")
    static OggVorbisInfo refixes$create(int channels, int sampleRate, double duration) {
        throw new AssertionError();
    }
}
