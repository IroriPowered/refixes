package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SetupAssetPacing;
import com.hypixel.hytale.protocol.packets.setup.RequestAssets;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.handlers.SetupPacketHandler;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SetupPacketHandler.class)
public abstract class MixinSetupStagePacing {

    @WrapOperation(
            method = "handle(Lcom/hypixel/hytale/protocol/packets/setup/RequestAssets;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenAccept(Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$continueOnWorker(
            CompletableFuture<Object> future, Consumer<Object> action, Operation<CompletableFuture<Void>> original) {
        SetupPacketHandler handler = (SetupPacketHandler) (Object) this;
        return SetupAssetPacing.continueOnWorker(handler, SetupAssetPacing.failedSetup(handler, future), action);
    }

    @WrapOperation(
            method = "handle(Lcom/hypixel/hytale/protocol/packets/setup/RequestAssets;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/io/handlers/SetupPacketHandler;continueStage(Ljava/lang/String;Ljava/time/Duration;Ljava/util/function/BooleanSupplier;)V"),
            require = 1,
            allow = 1)
    private void refixes$excludeDrainTime(
            SetupPacketHandler handler,
            String stage,
            Duration timeout,
            BooleanSupplier condition,
            Operation<Void> original,
            RequestAssets packet,
            @Local CompletableFuture<Void> future) {
        original.call(handler, stage, timeout, SetupAssetPacing.serverTime(handler, future, stage, timeout, condition));
    }

    @WrapOperation(
            method = "lambda$handle$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/modules/i18n/I18nModule;sendTranslations(Lcom/hypixel/hytale/server/core/io/PacketHandler;Ljava/lang/String;)V"),
            require = 1,
            allow = 1)
    private void refixes$awaitBeforeTranslations(
            I18nModule module, PacketHandler handler, String language, Operation<Void> original) {
        SetupAssetPacing.awaitDrain(handler);
        original.call(module, handler, language);
    }
}
