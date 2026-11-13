package cc.irori.refixes.early.mixin;

import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditSessionManager$PendingPaste")
public interface MixinPrefabPendingPasteAccess {

    @Accessor("path")
    Path refixes$getPath();
}
