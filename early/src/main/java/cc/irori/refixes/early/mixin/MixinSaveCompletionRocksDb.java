package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.SaveCompletions;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RocksDbChunkStorageProvider.Saver.class)
public class MixinSaveCompletionRocksDb {

    @IfHytale("<0.8.0-pre.1")
    @WrapOperation(
            method = {"saveChunkColumn", "saveEntitySection", "saveBlockComponentSection"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 3,
            allow = 3)
    private CompletableFuture<?> refixes$ownSectionSaveCompletion(
            CompletableFuture<?> future,
            BiConsumer<?, ?> action,
            Executor executor,
            Operation<CompletableFuture<?>> original) {
        return SaveCompletions.whenCompleteAsync(future, action, executor, original);
    }

    @IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
    @WrapOperation(
            method = {"saveChunkColumn", "saveBlockComponentSection"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 2,
            allow = 2)
    private CompletableFuture<?> refixes$ownSaveCompletion(
            CompletableFuture<?> future,
            BiConsumer<?, ?> action,
            Executor executor,
            Operation<CompletableFuture<?>> original) {
        return SaveCompletions.whenCompleteAsync(future, action, executor, original);
    }

    @IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
    @WrapOperation(
            method = "settleSectionSnapshot",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private static CompletableFuture<?> refixes$ownEntitySectionCompletion(
            CompletableFuture<?> future,
            BiConsumer<?, ?> action,
            Executor executor,
            Operation<CompletableFuture<?>> original) {
        return SaveCompletions.whenCompleteAsync(future, action, executor, original);
    }

    @IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
    @WrapOperation(
            method = "resaveAfter",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenRunAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private static CompletableFuture<?> refixes$ownSkippedEntityResave(
            CompletableFuture<?> future, Runnable action, Executor executor, Operation<CompletableFuture<?>> original) {
        return SaveCompletions.thenRunAsync(future, action, executor, original);
    }
}
