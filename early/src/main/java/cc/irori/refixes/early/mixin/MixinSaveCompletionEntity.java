package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SaveCompletions;
import com.hypixel.hytale.server.core.entity.EntitySavingSystem;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntitySavingSystem.class)
public class MixinSaveCompletionEntity {

    @WrapOperation(
            method = {"dispatchDirtyEntity"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private static CompletableFuture<?> refixes$ownSaveCompletion(
            CompletableFuture<?> future,
            BiConsumer<?, ?> action,
            Executor executor,
            Operation<CompletableFuture<?>> original) {
        return SaveCompletions.whenCompleteAsync(future, action, executor, original);
    }
}
