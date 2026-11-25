package cc.irori.refixes.early.accessor;

import cc.irori.hyinit.api.IfHytale;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;

@IfHytale("<0.8.0-pre.1")
public interface CommonAssetBlobAccess {

    WeakReference<CompletableFuture<byte[]>> refixes$getBlob();
}
