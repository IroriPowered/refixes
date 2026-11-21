package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.LightNeutralBlocks;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockTypeAssetMap.class)
public class MixinBlockTypeAssetMapLightGeneration {

    @Inject(
            method = {
                "putAll0(Lcom/hypixel/hytale/assetstore/codec/AssetCodec;Ljava/util/Map;)V",
                "remove0(Ljava/util/Set;)V",
                "clear()V"
            },
            at = @At("HEAD"),
            require = 3)
    private void refixes$assetsChanging(CallbackInfo ci) {
        LightNeutralBlocks.assetsChanging();
    }

    @Inject(
            method = {
                "putAll0(Lcom/hypixel/hytale/assetstore/codec/AssetCodec;Ljava/util/Map;)V",
                "remove0(Ljava/util/Set;)V",
                "clear()V"
            },
            at = @At("RETURN"),
            require = 3)
    private void refixes$assetsChanged(CallbackInfo ci) {
        LightNeutralBlocks.assetsChanged();
    }
}
