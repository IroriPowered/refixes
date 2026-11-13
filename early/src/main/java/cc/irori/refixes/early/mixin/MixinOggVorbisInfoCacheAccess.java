package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache;
import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache.OggVorbisInfo;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OggVorbisInfoCache.class)
public interface MixinOggVorbisInfoCacheAccess {

    @Accessor("vorbisFiles")
    static Map<String, OggVorbisInfo> refixes$getVorbisFiles() {
        throw new AssertionError();
    }
}
