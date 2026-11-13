package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.builtin.instances.removal.RemovalSystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RemovalSystem.class)
public class MixinSharedInstanceRemoval {

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;setDeleteOnUniverseStart(Z)V"),
            require = 1,
            allow = 1)
    private void refixes$preserveStorageOnRestart(
            WorldConfig config,
            boolean delete,
            Operation<Void> original,
            @Local(argsOnly = true) Store<ChunkStore> store) {
        original.call(config, delete && !refixes$isProtected(store));
    }

    @WrapOperation(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;setDeleteOnRemove(Z)V"),
            require = 1,
            allow = 1)
    private void refixes$preserveStorageOnRemove(
            WorldConfig config,
            boolean delete,
            Operation<Void> original,
            @Local(argsOnly = true) Store<ChunkStore> store) {
        original.call(config, delete && !refixes$isProtected(store));
    }

    @Unique
    private static boolean refixes$isProtected(Store<ChunkStore> store) {
        return EarlyOptions.SHARED_INSTANCES_STORAGE_AVAILABLE.get()
                && store.getExternalData()
                        .getWorld()
                        .getName()
                        .startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX);
    }
}
