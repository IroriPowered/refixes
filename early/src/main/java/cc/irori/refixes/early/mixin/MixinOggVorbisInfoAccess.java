package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache.OggVorbisInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(OggVorbisInfo.class)
public interface MixinOggVorbisInfoAccess {

    @Invoker("<init>")
    static OggVorbisInfo refixes$create(int channels, int sampleRate, double duration) {
        throw new AssertionError();
    }
}
