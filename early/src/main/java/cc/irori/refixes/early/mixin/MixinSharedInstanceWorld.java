package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinSharedInstanceWorld {

    @Inject(method = "init()Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void refixes$preserveStorageBeforeInit(CallbackInfoReturnable<CompletableFuture<World>> cir) {
        refixes$preserveStorage();
    }

    @Inject(method = "onShutdown", at = @At("HEAD"))
    private void refixes$preserveStorageBeforeShutdown(CallbackInfo ci) {
        refixes$preserveStorage();
    }

    @Unique
    private void refixes$preserveStorage() {
        World world = (World) (Object) this;
        if (!EarlyOptions.SHARED_INSTANCES_STORAGE_AVAILABLE.get()
                || !world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
            return;
        }

        WorldConfig config = world.getWorldConfig();
        if (config.isDeleteOnRemove() || config.isDeleteOnUniverseStart()) {
            config.setDeleteOnRemove(false);
            config.setDeleteOnUniverseStart(false);
            config.markChanged();
        }
    }
}
