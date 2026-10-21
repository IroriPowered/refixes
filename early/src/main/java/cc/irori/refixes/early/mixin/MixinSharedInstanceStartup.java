package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Universe.class)
public class MixinSharedInstanceStartup {

    @WrapOperation(
            method =
                    "loadWorldFromStart+ -> Ljava/util/function/Function;apply(Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;)Ljava/util/concurrent/CompletionStage;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;isDeleteOnUniverseStart()Z"),
            require = 1,
            allow = 1)
    private boolean refixes$preserveStoredWorld(
            WorldConfig config, Operation<Boolean> original, @Local(argsOnly = true) String name) {
        boolean delete = original.call(config);
        if (!EarlyOptions.SHARED_INSTANCES_STORAGE_AVAILABLE.get()
                || !name.startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
            return delete;
        }
        if (delete || config.isDeleteOnRemove()) {
            config.setDeleteOnUniverseStart(false);
            config.setDeleteOnRemove(false);
            config.markChanged();
        }
        return false;
    }
}
