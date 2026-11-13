package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SetupAssetPacing;
import com.hypixel.hytale.event.EventRegistration;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.asset.common.events.SendCommonAssetsEvent;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CommonAssetModule.class)
public abstract class MixinSetupAssetPacing {

    @SuppressWarnings("unchecked")
    @WrapOperation(
            method = "setup",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/event/EventRegistry;register(Ljava/lang/Class;Ljava/util/function/Consumer;)Lcom/hypixel/hytale/event/EventRegistration;",
                            ordinal = 0),
            require = 1,
            allow = 1)
    private EventRegistration<?, ?> refixes$chainSendAssets(
            EventRegistry registry,
            Class<?> eventClass,
            Consumer<?> consumer,
            Operation<EventRegistration<?, ?>> original) {
        if (eventClass != SendCommonAssetsEvent.class) return original.call(registry, eventClass, consumer);
        return registry.registerAsync(
                SendCommonAssetsEvent.class, SetupAssetPacing.chain((Consumer<SendCommonAssetsEvent>) consumer));
    }

    @Inject(
            method = "sendAssetsToPlayer(Lcom/hypixel/hytale/server/core/io/PacketHandler;Ljava/util/List;Z)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/asset/common/CommonAsset;getBlob()Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private void refixes$awaitBeforeAsset(
            PacketHandler packetHandler, java.util.List<?> toSend, boolean forceRebuild, CallbackInfo ci) {
        SetupAssetPacing.awaitDrain(packetHandler);
    }

    @WrapOperation(
            method = "sendAssetsToPlayer(Lcom/hypixel/hytale/server/core/io/PacketHandler;Ljava/util/List;Z)V",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"),
            require = 1,
            allow = 1)
    private Object refixes$joinBlobForOwner(
            CompletableFuture<?> blob,
            Operation<Object> original,
            PacketHandler packetHandler,
            java.util.List<?> toSend,
            boolean forceRebuild) {
        SetupAssetPacing.awaitBlob(packetHandler, blob);
        return original.call(blob);
    }
}
