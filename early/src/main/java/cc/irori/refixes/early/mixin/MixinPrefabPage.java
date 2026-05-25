package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.buildertools.prefablist.PrefabPage;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrefabPage.class)
public abstract class MixinPrefabPage {

    @Shadow
    private Path assetsCurrentDir;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void refixes$startTopLevel(CallbackInfo ci) {
        this.assetsCurrentDir = Paths.get("");
    }

    @Redirect(
            method = "handleAssetsNavigation",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/nio/file/Paths;get(Ljava/lang/String;[Ljava/lang/String;)Ljava/nio/file/Path;",
                            ordinal = 1),
            slice =
                    @Slice(
                            from = @At(value = "CONSTANT", args = "stringValue=~"),
                            to = @At(value = "CONSTANT", args = "stringValue=..")))
    private Path refixes$homeToTopLevel(String first, String[] more) {
        return Paths.get("");
    }
}
