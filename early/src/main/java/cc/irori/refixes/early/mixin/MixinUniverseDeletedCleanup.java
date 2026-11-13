package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.server.core.universe.Universe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Universe.class)
public abstract class MixinUniverseDeletedCleanup {

    @WrapOperation(
            method = "start()V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/util/io/FileUtil;deleteDirectory(Ljava/nio/file/Path;)V",
                            ordinal = 0),
            require = 1,
            allow = 1)
    private void refixes$retryDeletionNextStart(Path worldsDeleted, Operation<Void> original) {
        try {
            original.call(worldsDeleted);
        } catch (Exception e) {
            if (!(e instanceof IOException) && !(e instanceof UncheckedIOException)) throw e;
            Logs.logger()
                    .atWarning()
                    .withCause(e)
                    .log("Failed to complete deletion of %s, retrying on next start", worldsDeleted.toAbsolutePath());
        }
    }
}
