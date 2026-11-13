package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.CubicTransfer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkSavingSystems.Ticking.class)
public class MixinCubicTransferTickingColumn {

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/IChunkSaver;saveChunkColumn(IILcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/component/Ref;Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$acknowledgeColumnTransfers(
            IChunkSaver saver,
            int x,
            int z,
            Store<ChunkStore> store,
            Ref<ChunkStore> ref,
            Executor completionExecutor,
            Runnable onIoComplete,
            Operation<CompletableFuture<Void>> original) {
        List<CubicTransfer.Pending> captured = CubicTransfer.beforeColumnSave(saver, store, ref);
        CompletableFuture<Void> future = original.call(saver, x, z, store, ref, completionExecutor, onIoComplete);
        return CubicTransfer.afterColumnSave(captured, future);
    }
}
