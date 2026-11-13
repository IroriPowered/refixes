package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.PluginConfigWrites;
import com.hypixel.hytale.server.core.util.Config;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.bson.BsonDocument;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Config.class)
public abstract class MixinConfig<T> implements PluginConfigWrites.Access {
    @Shadow
    private T config;

    @Shadow
    private CompletableFuture<T> loadingConfig;

    @Unique
    private final Object refixes$loadLock = new Object();

    @Unique
    private boolean refixes$loadFailed;

    @Unique
    private CompletableFuture<Void> refixes$lastWrite;

    @Override
    public CompletableFuture<Void> refixes$pendingWrite() {
        synchronized (refixes$loadLock) {
            return refixes$lastWrite;
        }
    }

    @WrapMethod(method = "load", require = 1)
    private CompletableFuture<T> refixes$serializeLoad(Operation<CompletableFuture<T>> original) {
        synchronized (refixes$loadLock) {
            if (refixes$loadFailed) {
                loadingConfig = null;
                refixes$loadFailed = false;
            }
            return original.call();
        }
    }

    @WrapOperation(
            method = "load",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<T> refixes$publishLoad(Supplier<T> supplier, Operation<CompletableFuture<T>> original) {
        CompletableFuture<T> pending = new CompletableFuture<>();
        loadingConfig = pending;
        try {
            original.call(supplier).whenComplete((value, failure) -> {
                synchronized (refixes$loadLock) {
                    if (loadingConfig == pending) {
                        if (failure == null) loadingConfig = null;
                        else refixes$loadFailed = true;
                    }
                }
                if (failure == null) pending.complete(value);
                else pending.completeExceptionally(failure);
            });
        } catch (Throwable failure) {
            loadingConfig = null;
            throw failure;
        }
        return pending;
    }

    @WrapOperation(
            method = {"load", "lambda$load$0"},
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lcom/hypixel/hytale/server/core/util/Config;loadingConfig:Ljava/util/concurrent/CompletableFuture;",
                            opcode = Opcodes.PUTFIELD),
            require = 2,
            allow = 2)
    private void refixes$ownLoadPublication(Config<T> instance, CompletableFuture<T> value, Operation<Void> original) {}

    @WrapMethod(method = "get", require = 1)
    private T refixes$getPublishedConfig(Operation<T> original) {
        CompletableFuture<T> pending;
        T value;
        synchronized (refixes$loadLock) {
            pending = loadingConfig;
            value = config;
        }
        if (pending != null) return pending.join();
        if (value == null) throw new IllegalStateException("Config is not loaded");
        return value;
    }

    @WrapMethod(method = "save", require = 1)
    private CompletableFuture<Void> refixes$serializeSave(Operation<CompletableFuture<Void>> original) {
        synchronized (refixes$loadLock) {
            return original.call();
        }
    }

    @WrapOperation(
            method = "save",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/util/BsonUtil;writeDocument(Ljava/nio/file/Path;Lorg/bson/BsonDocument;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$orderWrites(
            Path path, BsonDocument document, Operation<CompletableFuture<Void>> original) {
        if (refixes$lastWrite == null) refixes$lastWrite = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> write = refixes$lastWrite
                .handle((ignored, failure) -> null)
                .thenCompose(ignored -> original.call(path, document));
        refixes$lastWrite = write;
        return write.copy();
    }
}
