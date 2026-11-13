package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.RetiredWorlds;
import com.hypixel.hytale.builtin.creativehub.CreativeHubPlugin;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CreativeHubPlugin.class)
public class MixinCreativeHubPluginRetirement {

    @WrapMethod(
            method =
                    "spawnPermanentWorldFromTemplate(Ljava/lang/String;Ljava/lang/String;Ljava/util/function/Consumer;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<World> refixes$spawnAfterRetirement(
            String instanceAssetName,
            String permanentWorldName,
            Consumer<WorldConfig> configCustomizer,
            Operation<CompletableFuture<World>> original) {
        Universe universe = Universe.get();
        if (universe.getWorld(permanentWorldName) != null) {
            return original.call(instanceAssetName, permanentWorldName, configCustomizer);
        }
        Path savePath = universe.validateWorldPath(permanentWorldName);
        CompletableFuture<Void> released =
                RetiredWorlds.afterRelease(permanentWorldName, savePath, () -> CompletableFuture.completedFuture(null));
        if (released.isDone() && !released.isCompletedExceptionally()) {
            return original.call(instanceAssetName, permanentWorldName, configCustomizer);
        }
        return released.thenCompose(ignored -> original.call(instanceAssetName, permanentWorldName, configCustomizer));
    }
}
