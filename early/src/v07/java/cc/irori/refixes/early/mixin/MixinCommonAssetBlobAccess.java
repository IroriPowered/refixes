package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.accessor.CommonAssetBlobAccess;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@IfHytale("<0.8.0-pre.1")
@Mixin(CommonAsset.class)
public interface MixinCommonAssetBlobAccess extends CommonAssetBlobAccess {

    @Override
    @Accessor("blob")
    WeakReference<CompletableFuture<byte[]>> refixes$getBlob();
}
