package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SetupAssetPacing;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.hypixel.hytale.server.core.asset.common.CommonAssetModule$CommonAssetMonitorHandler")
public abstract class MixinCommonAssetMonitorBroadcast {

    @WrapOperation(
            method = "accept(Ljava/util/Map;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/asset/common/CommonAssetModule;sendAssets(Ljava/util/List;Z)V"),
            require = 1,
            allow = 1)
    private void refixes$markMonitorBroadcast(
            CommonAssetModule module, List<CommonAsset> assets, boolean forceRebuild, Operation<Void> original) {
        SetupAssetPacing.monitorBroadcast(() -> original.call(module, assets, forceRebuild));
    }
}
