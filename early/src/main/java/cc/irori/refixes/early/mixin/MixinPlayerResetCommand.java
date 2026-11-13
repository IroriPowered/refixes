package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.commands.player.PlayerResetCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerResetCommand.class)
public abstract class MixinPlayerResetCommand {
    @WrapOperation(
            method =
                    "execute(Lcom/hypixel/hytale/server/core/command/system/CommandContext;Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/component/Store;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/Universe;resetPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<PlayerRef> refixes$keepReset(
            Universe universe,
            PlayerRef player,
            Operation<CompletableFuture<PlayerRef>> original,
            @Share("reset") LocalRef<CompletableFuture<PlayerRef>> reset) {
        CompletableFuture<PlayerRef> future = original.call(universe, player);
        reset.set(future);
        return future;
    }

    @WrapOperation(
            method =
                    "execute(Lcom/hypixel/hytale/server/core/command/system/CommandContext;Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/component/Store;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/command/system/CommandContext;sendMessage(Lcom/hypixel/hytale/server/core/Message;)V"),
            require = 1,
            allow = 1)
    private void refixes$reportReset(
            CommandContext context,
            Message success,
            Operation<Void> original,
            @Share("reset") LocalRef<CompletableFuture<PlayerRef>> reset) {
        reset.get().whenComplete((player, error) -> {
            try {
                if (error == null) {
                    original.call(context, success);
                    return;
                }
                Throwable cause = error;
                while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                Logs.logger()
                        .at(Level.SEVERE)
                        .withCause(new SkipSentryException(cause))
                        .log(
                                "Failed to execute command %s for %s",
                                context.getInputString(), context.sender().getUsername());
                context.sendMessage(Message.translation("server.modules.command.error")
                        .param("cmd", context.getInputString())
                        .param(
                                "msg",
                                cause.getMessage() == null
                                        ? Message.translation("server.modules.command.noProvidedExceptionMessage")
                                        : Message.raw(cause.getMessage())));
            } catch (Throwable failure) {
                Logs.logger().at(Level.SEVERE).withCause(failure).log("Exception while running that command:");
            }
        });
    }
}
