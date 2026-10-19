package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.adventure.worldevents.GlobalWorldEventManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GlobalWorldEventManager.class)
public abstract class MixinGlobalWorldEventManager {
    @Unique
    private CompletableFuture<?> refixes$saveTail = CompletableFuture.completedFuture(null);

    @WrapMethod(method = "saveAsync")
    private void refixes$serializeSave(Operation<Void> original) {
        synchronized (this) {
            original.call();
        }
    }

    @WrapOperation(
            method = "saveAsync",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<UUID[]> refixes$queueSnapshot(
            Supplier<UUID[]> snapshot, Operation<CompletableFuture<UUID[]>> original) {
        return refixes$saveTail.handle((ignored, failure) -> null).thenCompose(ignored -> original.call(snapshot));
    }

    @WrapOperation(
            method = "saveAsync",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;whenCompleteAsync(Ljava/util/function/BiConsumer;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> refixes$trackSave(
            CompletableFuture<Void> save,
            BiConsumer<? super Void, ? super Throwable> callback,
            Operation<CompletableFuture<Void>> original) {
        CompletableFuture<Void> result = original.call(save, callback);
        refixes$saveTail = result;
        return result;
    }
}
