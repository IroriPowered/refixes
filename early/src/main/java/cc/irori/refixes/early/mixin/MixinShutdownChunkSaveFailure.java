package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSavingSystems.WorldRemoved.class)
public abstract class MixinShutdownChunkSaveFailure {

    @WrapOperation(
            method = "onSystemRemovedFromStore",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"),
            require = 1,
            allow = 1)
    private Object refixes$finishShutdownAfterFailedSave(
            CompletableFuture<?> saving,
            Operation<Object> original,
            @Local(argsOnly = true) Store<ChunkStore> store,
            @Share("refixes$saveFailed") LocalBooleanRef failed) {
        try {
            return original.call(saving);
        } catch (CompletionException failure) {
            failed.set(true);
            store.getExternalData()
                    .getWorld()
                    .getLogger()
                    .at(Level.WARNING)
                    .withCause(failure.getCause())
                    .log("Some chunks failed to save on shutdown");
            return null;
        }
    }

    @WrapWithCondition(
            method = "onSystemRemovedFromStore",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/hytale/logger/HytaleLogger$Api;log(Ljava/lang/String;)V",
                            ordinal = 3),
            require = 1,
            allow = 1)
    private boolean refixes$skipDoneAfterFailedSave(
            HytaleLogger.Api api, String message, @Share("refixes$saveFailed") LocalBooleanRef failed) {
        return !failed.get();
    }
}
