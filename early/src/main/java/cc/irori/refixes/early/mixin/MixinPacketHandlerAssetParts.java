package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.AssetPartSnapshots;
import com.hypixel.hytale.protocol.CachedPacket;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PacketHandler.class)
public abstract class MixinPacketHandlerAssetParts {

    @WrapOperation(
            method = "handleOutboundAndCachePacket",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/protocol/CachedPacket;cache(Lcom/hypixel/hytale/protocol/ToClientPacket;)Lcom/hypixel/hytale/protocol/CachedPacket;"),
            require = 1,
            allow = 1)
    private CachedPacket<?> refixes$shareAssetPart(ToClientPacket packet, Operation<CachedPacket<?>> original) {
        return AssetPartSnapshots.cache(packet, original);
    }
}
