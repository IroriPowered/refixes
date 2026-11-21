package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SetupAssetPacing;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.hypixel.hytale.server.core.universe.Universe;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CommonAssetModule.class)
public abstract class MixinCommonAssetBroadcast {

    @WrapOperation(
            method = {"sendAssets(Ljava/util/List;Z)V", "lambda$sendAsset$0"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/Universe;broadcastPacket([Lcom/hypixel/hytale/protocol/ToClientPacket;)V"),
            require = 2,
            allow = 2)
    private void refixes$paceAssetBroadcast(Universe universe, ToClientPacket[] packets, Operation<Void> original) {
        SetupAssetPacing.broadcast(() -> original.call(universe, packets));
    }
}
