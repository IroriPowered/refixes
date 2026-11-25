package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.RetiredWorlds;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(Universe.class)
public abstract class MixinUniverseTransferCleanup {

    @Shadow
    private Map<UUID, World> worldsByUuid;

    @WrapOperation(
            method = "lambda$makeWorld$0",
            at = @At(value = "NEW", target = "com/hypixel/hytale/server/core/universe/world/World"),
            require = 1,
            allow = 1)
    private World refixes$admitCreatedWorld(String name, Path path, WorldConfig config, Operation<World> original) {
        World world = original.call(name, path, config);
        RetiredWorlds.created(world);
        return world;
    }

    @WrapMethod(
            method =
                    "makeWorld(Ljava/lang/String;Ljava/nio/file/Path;Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;Z)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<World> refixes$openAfterRetirement(
            String name,
            Path savePath,
            WorldConfig worldConfig,
            boolean start,
            Operation<CompletableFuture<World>> original) {
        return RetiredWorlds.afterRelease(name, savePath, () -> original.call(name, savePath, worldConfig, start));
    }

    @WrapMethod(method = "removeWorld")
    private boolean refixes$holdRemovalTransaction(String name, Operation<Boolean> original) {
        World world = ((Universe) (Object) this).getWorld(name);
        if (world == null) return original.call(name);
        Path path = RetiredWorlds.beginRemoval(world);
        try {
            return original.call(name);
        } finally {
            RetiredWorlds.endRemoval(path);
        }
    }

    @WrapMethod(method = "removeWorldExceptionally")
    private void refixes$holdExceptionalRemovalTransaction(
            String name, Map<UUID, PlayerRef> players, Operation<Void> original) {
        World world = ((Universe) (Object) this).getWorld(name);
        if (world == null) {
            original.call(name, players);
            return;
        }
        Path path = RetiredWorlds.beginRemoval(world);
        try {
            original.call(name, players);
        } finally {
            RetiredWorlds.endRemoval(path);
        }
    }

    @WrapOperation(
            method = "lambda$makeWorld$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/Map;putIfAbsent(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                            ordinal = 0),
            require = 1,
            allow = 1)
    private Object refixes$claimFreePath(
            Map<String, World> worlds,
            Object key,
            Object world,
            Operation<Object> original,
            @Local(argsOnly = true) String name) {
        try {
            World created = (World) world;
            return RetiredWorlds.claim(
                    name, created.getSavePath(), worlds, (String) key, created, created.getWorldConfig(), () ->
                            (World) original.call(worlds, key, world));
        } catch (RetiredWorlds.ReopenConflict conflict) {
            worldsByUuid.computeIfPresent(
                    ((World) world).getWorldConfig().getUuid(), (id, current) -> current == world ? null : current);
            throw conflict;
        }
    }
}
