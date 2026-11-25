package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache;
import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache.OggVorbisInfo;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@IfHytale("<0.8.0-pre.1")
@Mixin(OggVorbisInfoCache.class)
public interface MixinOggVorbisInfoCacheAccess {

    @Accessor("vorbisFiles")
    static Map<String, OggVorbisInfo> refixes$getVorbisFiles() {
        throw new AssertionError();
    }
}
