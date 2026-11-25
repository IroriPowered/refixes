package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.assetstore.AssetPack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(targets = "com.hypixel.hytale.server.core.asset.common.CommonAssetModule$1")
public abstract class MixinCommonAssetHashCleanup {

    @Shadow
    @Final
    AssetPack val$pack;

    @WrapOperation(
            method =
                    "visitFile(Ljava/nio/file/Path;Ljava/nio/file/attribute/BasicFileAttributes;)Ljava/nio/file/FileVisitResult;",
            at = @At(value = "INVOKE", target = "Ljava/nio/file/Files;deleteIfExists(Ljava/nio/file/Path;)Z"),
            require = 1,
            allow = 1)
    private boolean refixes$keepImmutablePackHashes(Path path, Operation<Boolean> original) {
        return !val$pack.isImmutable() && original.call(path);
    }
}
