package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldMapManager.class)
public abstract class MixinWorldMapTickingStop {

    @Shadow
    @Final
    private World world;

    @Unique
    private boolean refixes$stopping;

    @Inject(method = "updateTickingState(Z)V", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void refixes$waitForStop(boolean started, CallbackInfo ci) {
        if (refixes$stopping) ci.cancel();
    }

    @WrapOperation(
            method = "updateTickingState(Z)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/hytale/server/core/universe/world/worldmap/WorldMapManager;stop()V"),
            require = 1,
            allow = 1)
    private void refixes$stopOffWorldThread(WorldMapManager manager, Operation<Void> original) {
        if (!world.isInThread()) {
            original.call(manager);
            return;
        }
        refixes$stopping = true;
        Thread.ofPlatform()
                .name("WorldMap - " + world.getName() + " - Stop")
                .daemon()
                .start(() -> {
                    try {
                        original.call(manager);
                    } finally {
                        synchronized (manager) {
                            refixes$stopping = false;
                            if (world.isAlive() && world.isStarted()) manager.updateTickingState(false);
                        }
                    }
                });
    }
}
