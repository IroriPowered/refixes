package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.CubicTransfer;
import cc.irori.refixes.early.util.SaveCompletions;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(ChunkSavingSystems.WorldRemoved.class)
public class MixinCubicTransferShutdown {

    @WrapOperation(
            method = "onSystemRemovedFromStore",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/component/ChunkSavingSystems;saveChunksInWorld(Lcom/hypixel/hytale/component/Store;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$saveAfterDepartures(
            Store<ChunkStore> store, Executor executor, Operation<CompletableFuture<Void>> original) {
        World world = store.getExternalData().getWorld();
        return SaveCompletions.settle(
                world,
                store,
                CubicTransfer.finalSave(world, store, original.call(store, executor)),
                () -> original.call(store, executor));
    }

    @WrapMethod(method = "onSystemRemovedFromStore")
    private void refixes$forgetDepartures(Store<ChunkStore> store, Operation<Void> original) {
        try {
            original.call(store);
        } finally {
            CubicTransfer.stopped(store);
        }
    }
}
