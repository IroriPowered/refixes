package cc.irori.refixes.early.util;

import cc.irori.refixes.early.accessor.CommonAssetBlobAccess;
import cc.irori.refixes.early.mixin.MixinCommonAssetBlobAccess;
import cc.irori.refixes.early.mixin.MixinOggVorbisInfoAccess;
import cc.irori.refixes.early.mixin.MixinOggVorbisInfoCacheAccess;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry;
import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache;
import com.hypixel.hytale.server.core.asset.common.OggVorbisInfoCache.OggVorbisInfo;
import com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

public final class SoundHeaderInfo {

    private static final boolean NATIVE_READERS =
            hasOnlyMixin(OggVorbisInfoCache.class, MixinOggVorbisInfoCacheAccess.class.getName())
                    && hasOnlyMixin(CommonAsset.class, MixinCommonAssetBlobAccess.class.getName())
                    && hasOnlyMixin(FileCommonAsset.class, "");
    private static final int PREFIX_LENGTH = 256;
    private static final int ID_FIELDS_LENGTH = 16;

    private SoundHeaderInfo() {}

    public static OggVorbisInfo getNow(String name, Operation<OggVorbisInfo> original) {
        if (!NATIVE_READERS || name == null) return original.call(name);
        OggVorbisInfo cached =
                MixinOggVorbisInfoCacheAccess.refixes$getVorbisFiles().get(name);
        if (cached != null) return cached;
        CommonAsset asset = CommonAssetRegistry.getByName(name);
        if (asset == null || asset.getClass() != FileCommonAsset.class) return original.call(name);
        WeakReference<CompletableFuture<byte[]>> blob = ((CommonAssetBlobAccess) asset).refixes$getBlob();
        if (blob == null || blob.get() != null) return original.call(name);
        byte[] prefix;
        try (InputStream input = Files.newInputStream(((FileCommonAsset) asset).getFile())) {
            prefix = input.readNBytes(PREFIX_LENGTH);
        } catch (IOException | RuntimeException failure) {
            return original.call(name);
        }
        OggVorbisInfo info = parse(prefix);
        return info != null ? info : original.call(name);
    }

    private static boolean hasOnlyMixin(Class<?> type, String allowedMixin) {
        try {
            for (Method method : type.getDeclaredMethods()) {
                MixinMerged merged = method.getAnnotation(MixinMerged.class);
                if (merged != null && !allowedMixin.equals(merged.mixin())) return false;
            }
            return true;
        } catch (LinkageError | SecurityException failure) {
            return false;
        }
    }

    static OggVorbisInfo parse(byte[] prefix) {
        for (int i = 0, end = prefix.length - 7; i <= end; i++) {
            if (prefix[i] != 0x01
                    || prefix[i + 1] != 'v'
                    || prefix[i + 2] != 'o'
                    || prefix[i + 3] != 'r'
                    || prefix[i + 4] != 'b'
                    || prefix[i + 5] != 'i'
                    || prefix[i + 6] != 's') continue;
            if (i + ID_FIELDS_LENGTH > prefix.length) return null;
            int channels = Byte.toUnsignedInt(prefix[i + 11]);
            int sampleRate = Byte.toUnsignedInt(prefix[i + 12])
                    | Byte.toUnsignedInt(prefix[i + 13]) << 8
                    | Byte.toUnsignedInt(prefix[i + 14]) << 16
                    | Byte.toUnsignedInt(prefix[i + 15]) << 24;
            return MixinOggVorbisInfoAccess.refixes$create(channels, sampleRate, -1);
        }
        return null;
    }
}
