package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.PatchCounters;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.VersionedExtraInfo;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ExtraInfo.class)
public class MixinCodecMetadata {

    @Unique
    private static final Object2ObjectOpenHashMap<String, Object> REFIXES$UNREAD_METADATA =
            new Object2ObjectOpenHashMap<>(0);

    @Redirect(
            method = "<init>()V",
            at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/Object2ObjectOpenHashMap"),
            require = 1,
            allow = 1)
    private Object2ObjectOpenHashMap<String, Object> refixes$shareDelegatedMetadata() {
        if (PatchCounters.ENABLED) PatchCounters.CODEC_METADATA.countReached();
        if ((Object) this instanceof VersionedExtraInfo) {
            if (PatchCounters.ENABLED) PatchCounters.CODEC_METADATA.countEffect();
            return REFIXES$UNREAD_METADATA;
        }
        return new Object2ObjectOpenHashMap<>();
    }
}
