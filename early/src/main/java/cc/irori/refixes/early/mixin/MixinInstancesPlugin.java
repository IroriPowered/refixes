package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InstancesPlugin.class)
public abstract class MixinInstancesPlugin {

    @ModifyExpressionValue(
            method =
                    "spawnInstance+ -> Ljava/util/function/Function;apply(Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;)Ljava/util/concurrent/CompletionStage;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/builtin/instances/config/InstanceWorldConfig;getInstanceKey()Ljava/lang/String;",
                            ordinal = 0),
            require = 1,
            allow = 1)
    private String refixes$sharedWorldKey(String authoredKey, @Local(argsOnly = true, ordinal = 0) String name) {
        if (!EarlyOptions.SHARED_INSTANCES_STORAGE_AVAILABLE.get() || authoredKey != null || refixes$isExcluded(name)) {
            return authoredKey;
        }
        return SharedInstanceConstants.SHARED_INSTANCE_PREFIX + InstancesPlugin.safeName(name);
    }

    @WrapOperation(
            method =
                    "spawnInstance+ -> Ljava/util/function/Function;apply(Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;)Ljava/util/concurrent/CompletionStage;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"),
            require = 1,
            allow = 1)
    private Object refixes$loadPersistedSharedInstance(
            Map<String, CompletableFuture<World>> pendingSpawns,
            Object worldKey,
            Function<String, CompletableFuture<World>> create,
            Operation<Object> original,
            @Local(argsOnly = true, ordinal = 0) String name,
            @Local(argsOnly = true, ordinal = 1) String worldName,
            @Local(argsOnly = true) WorldConfig config) {
        if (!EarlyOptions.SHARED_INSTANCES_STORAGE_AVAILABLE.get() || worldName != null || refixes$isExcluded(name)) {
            return original.call(pendingSpawns, worldKey, create);
        }
        InstanceWorldConfig instanceConfig = InstanceWorldConfig.get(config);
        if (instanceConfig != null && instanceConfig.getInstanceKey() != null) {
            return original.call(pendingSpawns, worldKey, create);
        }
        String sharedKey = (String) worldKey;
        Universe universe = Universe.get();
        if (!sharedKey.startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)
                || !universe.isWorldLoadable(sharedKey)) {
            return original.call(pendingSpawns, worldKey, create);
        }
        Function<String, CompletableFuture<World>> load = universe::loadWorld;
        CompletableFuture<?> future = (CompletableFuture<?>) original.call(pendingSpawns, worldKey, load);
        future.whenComplete((world, error) -> pendingSpawns.remove(sharedKey, future));
        return future;
    }

    @Unique
    private static boolean refixes$isExcluded(String name) {
        for (String prefix : EarlyOptions.SHARED_INSTANCES_EXCLUDED_PREFIXES.get()) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
