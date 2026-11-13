package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SetupAssetPacing;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.server.core.asset.AssetRegistryLoader;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AssetRegistryLoader.class)
public abstract class MixinSetupConfigPacing {

    @WrapOperation(
            method = "sendAssets(Lcom/hypixel/hytale/server/core/io/PacketHandler;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/asset/HytaleAssetStore;sendAssets(Ljava/util/function/Consumer;)V"),
            require = 1,
            allow = 1)
    private static void refixes$awaitBeforeStore(
            HytaleAssetStore<?, ?, ?> store,
            Consumer<ToClientPacket[]> consumer,
            Operation<Void> original,
            PacketHandler packetHandler) {
        SetupAssetPacing.awaitDrain(packetHandler);
        original.call(store, consumer);
    }
}
