package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.server.core.HytaleServerConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@IfHytale("<0.8.0-pre.1")
@Mixin(HytaleServerConfig.class)
public abstract class MixinServerConfigSaveChain {

    @Unique
    private static final Object refixes$SAVE_LOCK = new Object();

    @Unique
    private static CompletableFuture<Void> refixes$lastSave = CompletableFuture.completedFuture(null);

    @WrapMethod(
            method =
                    "save(Ljava/nio/file/Path;Lcom/hypixel/hytale/server/core/HytaleServerConfig;)Ljava/util/concurrent/CompletableFuture;")
    private static CompletableFuture<Void> refixes$chainSave(
            Path path, HytaleServerConfig config, Operation<CompletableFuture<Void>> original) {
        synchronized (refixes$SAVE_LOCK) {
            CompletableFuture<Void> save = refixes$lastSave
                    .handle((ignored, failure) -> null)
                    .thenCompose(ignored -> original.call(path, config));
            refixes$lastSave = save;
            return save.copy();
        }
    }
}
