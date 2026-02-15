package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.hytalegenerator.assets.props.prefabprop.PrefabLoader;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.PrefabBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Files;
import java.nio.file.Path;

@Mixin(PrefabLoader.class)
public class MixinPrefabLoader {

    @Inject(
            method = "loadPrefabBufferAt",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void refixes$validatePrefabPath(Path filePath, CallbackInfoReturnable<PrefabBuffer> cir) {
        if (!Files.exists(filePath)) {
            cir.cancel();
            cir.setReturnValue(null);
        }
    }
}
