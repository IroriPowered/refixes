package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.RetiredWorlds;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(InstancesPlugin.class)
public class MixinInstancesPluginRetirement {

    @WrapMethod(
            method =
                    "copyAndLoadInstance(Ljava/lang/String;Ljava/nio/file/Path;Ljava/lang/String;Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<World> refixes$copyAfterRetirement(
            String name,
            Path assetPath,
            String worldName,
            WorldConfig config,
            Operation<CompletableFuture<World>> original) {
        Path savePath = Universe.get().validateWorldPath(worldName);
        return RetiredWorlds.<Void>afterRelease(worldName, savePath, () -> CompletableFuture.completedFuture(null))
                .thenCompose(ignored -> original.call(name, assetPath, worldName, config));
    }
}
