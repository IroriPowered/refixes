package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.commands.world.worldgen.WorldGenReloadCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(WorldGenReloadCommand.class)
public class MixinWorldGenReload {
    @WrapMethod(
            method =
                    "clearChunks(Lcom/hypixel/hytale/server/core/command/system/CommandContext;Lcom/hypixel/hytale/server/core/universe/world/World;)Ljava/util/concurrent/CompletableFuture;",
            require = 1)
    private static CompletableFuture<Void> refixes$releaseAfterSetupFailure(
            CommandContext context,
            World world,
            Operation<CompletableFuture<Void>> original,
            @Share("savingLock") LocalRef<AtomicBoolean> savingLock) {
        AtomicBoolean held = new AtomicBoolean();
        savingLock.set(held);
        try {
            return original.call(context, world);
        } catch (Throwable failure) {
            if (held.compareAndSet(true, false)) {
                try {
                    world.unlockSaving();
                } catch (Throwable unlockFailure) {
                    if (unlockFailure != failure) failure.addSuppressed(unlockFailure);
                }
            }
            throw failure;
        }
    }

    @WrapOperation(
            method = "clearChunks",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/universe/world/World;lockSaving()V"),
            require = 1,
            allow = 1)
    private static void refixes$ownSavingLock(
            World world, Operation<Void> original, @Share("savingLock") LocalRef<AtomicBoolean> savingLock) {
        original.call(world);
        savingLock.get().set(true);
    }

    @ModifyExpressionValue(
            method = "clearChunks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/ChunkStore;getSaver()Lcom/hypixel/hytale/server/core/universe/world/storage/IChunkSaver;"),
            require = 1,
            allow = 1)
    private static IChunkSaver refixes$releaseWithoutSaver(
            IChunkSaver saver,
            @Local(argsOnly = true) World world,
            @Share("savingLock") LocalRef<AtomicBoolean> savingLock) {
        if (saver == null && savingLock.get().compareAndSet(true, false)) world.unlockSaving();
        return saver;
    }

    @WrapMethod(
            method =
                    "lambda$clearChunks$1(Lcom/hypixel/hytale/server/core/universe/world/storage/IChunkSaver;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/server/core/command/system/CommandContext;Lit/unimi/dsi/fastutil/longs/LongList;)Ljava/util/concurrent/CompletionStage;",
            require = 1)
    private static CompletionStage<Void> refixes$drainIssuedDeletions(
            IChunkSaver saver,
            World world,
            CommandContext context,
            LongList indexes,
            Operation<CompletionStage<Void>> original,
            @Share("issuedDeletions") LocalRef<ObjectArrayList<CompletableFuture<Void>>> issuedDeletions) {
        try {
            return original.call(saver, world, context, indexes);
        } catch (Throwable failure) {
            ObjectArrayList<CompletableFuture<Void>> issued = issuedDeletions.get();
            if (issued == null || issued.isEmpty()) return CompletableFuture.failedFuture(failure);
            return CompletableFuture.allOf(issued.toArray(CompletableFuture[]::new))
                    .handle((ignored, pendingFailure) -> {
                        if (pendingFailure != null && pendingFailure != failure) failure.addSuppressed(pendingFailure);
                        throw new CompletionException(failure);
                    });
        }
    }

    @WrapOperation(
            method =
                    "lambda$clearChunks$1(Lcom/hypixel/hytale/server/core/universe/world/storage/IChunkSaver;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/server/core/command/system/CommandContext;Lit/unimi/dsi/fastutil/longs/LongList;)Ljava/util/concurrent/CompletionStage;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/IChunkSaver;removeHolder(II)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private static CompletableFuture<Void> refixes$trackIssuedDeletions(
            IChunkSaver saver,
            int x,
            int z,
            Operation<CompletableFuture<Void>> original,
            @Local ObjectArrayList<CompletableFuture<Void>> futures,
            @Share("issuedDeletions") LocalRef<ObjectArrayList<CompletableFuture<Void>>> issuedDeletions) {
        issuedDeletions.set(futures);
        return original.call(saver, x, z);
    }

    @WrapOperation(
            method = "clearChunks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenComposeAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;",
                            ordinal = 1),
            require = 1,
            allow = 1)
    private static CompletableFuture<Void> refixes$releaseBeforeRegeneration(
            CompletableFuture<Void> deletion,
            Function<Void, CompletionStage<Void>> regenerate,
            Executor executor,
            Operation<CompletableFuture<Void>> original,
            @Local(argsOnly = true) CommandContext context,
            @Local(argsOnly = true) World world,
            @Share("savingLock") LocalRef<AtomicBoolean> savingLock) {
        AtomicBoolean held = savingLock.get();
        CompletableFuture<Void> released = deletion.whenCompleteAsync(
                (ignored, failure) -> {
                    if (held.compareAndSet(true, false)) {
                        world.unlockSaving();
                        context.sendMessage(Message.translation("server.commands.worldgen.reload.chunkSavingEnabled"));
                    }
                },
                executor);
        return original.call(released, regenerate, executor);
    }

    @WrapOperation(
            method = "clearChunks",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenRunAsync(Ljava/lang/Runnable;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private static CompletableFuture<Void> refixes$removeLateUnlock(
            CompletableFuture<Void> regeneration,
            Runnable unlock,
            Executor executor,
            Operation<CompletableFuture<Void>> original) {
        return regeneration;
    }
}
