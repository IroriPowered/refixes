package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.CubicTransfer;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RocksDbChunkStorageProvider.Saver.class)
public class MixinCubicTransferSaver {

    @WrapMethod(
            method =
                    "saveEntitySection(IIILcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/EntitySection;Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<Void> refixes$trackSectionSave(
            int x,
            int y,
            int z,
            Store<ChunkStore> store,
            EntitySection section,
            Executor completionExecutor,
            Runnable onIoComplete,
            Operation<CompletableFuture<Void>> original) {
        List<CubicTransfer.Snapshot> tracked = CubicTransfer.beforeSectionSave(store, section);
        CompletableFuture<Void> future = original.call(x, y, z, store, section, completionExecutor, onIoComplete);
        return CubicTransfer.afterSectionSave(tracked, future);
    }

    @WrapMethod(
            method =
                    "saveEntity(Ljava/util/UUID;Lcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/component/Ref;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<Void> refixes$trackEntitySave(
            UUID uuid, Store<EntityStore> store, Ref<EntityStore> ref, Operation<CompletableFuture<Void>> original) {
        CubicTransfer.Pending pending = CubicTransfer.beforeEntitySave(store, ref, uuid);
        CompletableFuture<Void> future = original.call(uuid, store, ref);
        return CubicTransfer.afterEntitySave(pending, future);
    }

    @WrapOperation(
            method =
                    "saveEntitySection(IIILcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/server/core/universe/world/chunk/section/EntitySection;Ljava/util/concurrent/Executor;Ljava/lang/Runnable;)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/provider/RocksDbChunkStorageProvider$Saver;collectEntityUuidsFromHolders(Ljava/util/List;Lcom/hypixel/hytale/component/ComponentRegistry$Data;Ljava/util/List;)V"),
            require = 1,
            allow = 1)
    private void refixes$retainDepartedUuids(
            List<Holder<EntityStore>> holders,
            ComponentRegistry.Data<EntityStore> data,
            List<UUID> uuids,
            Operation<Void> original,
            @Local EntitySection section,
            @Local(argsOnly = true) Store<ChunkStore> store) {
        original.call(holders, data, uuids);
        CubicTransfer.retain(store, section, uuids);
    }
}
