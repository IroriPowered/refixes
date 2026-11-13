package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.RetiredWorlds;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.WorldConfigProvider;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Universe.class)
public abstract class MixinUniverseRetirement {

    @WrapMethod(method = "loadWorld")
    private CompletableFuture<World> refixes$readAfterRetirement(
            String name, Operation<CompletableFuture<World>> original) {
        Path savePath = ((Universe) (Object) this).validateWorldPath(name);
        return RetiredWorlds.afterRelease(
                name, savePath, true, () -> RetiredWorlds.read(savePath, () -> original.call(name)));
    }

    @WrapOperation(
            method = "loadWorld",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/WorldConfigProvider;load(Ljava/nio/file/Path;Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<WorldConfig> refixes$trackConfigRead(
            WorldConfigProvider provider,
            Path savePath,
            String name,
            Operation<CompletableFuture<WorldConfig>> original) {
        return RetiredWorlds.bindRead(original.call(provider, savePath, name));
    }
}
