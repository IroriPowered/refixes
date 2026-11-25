package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@IfHytale("<0.8.0-pre.1")
@Mixin(targets = "com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditSessionManager$PendingPaste")
public interface MixinPrefabPendingPasteAccess {

    @Accessor("path")
    Path refixes$getPath();
}
