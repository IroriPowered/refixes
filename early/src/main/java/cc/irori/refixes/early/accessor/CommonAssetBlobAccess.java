package cc.irori.refixes.early.accessor;

import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;

public interface CommonAssetBlobAccess {

    WeakReference<CompletableFuture<byte[]>> refixes$getBlob();
}
