package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Skip the per-Ref filler-removal lambda when asyncRef is null (pre-release NPE spam).
@Mixin(FillerBlockUtil.class)
public class MixinFillerBlockUtil {

    @Inject(method = "lambda$removeOrphanedFillers$0", at = @At("HEAD"), cancellable = true)
    private static void refixes$skipNullAsyncRef(
            int p0,
            ComponentAccessor<?> accessor,
            int p2,
            int p3,
            int p4,
            int p5,
            int p6,
            int p7,
            int p8,
            int p9,
            int p10,
            FillerBlockUtil.ChangeReason reason,
            Ref<?> asyncRef,
            CallbackInfo ci) {
        if (asyncRef == null) {
            ci.cancel();
        }
    }
}
