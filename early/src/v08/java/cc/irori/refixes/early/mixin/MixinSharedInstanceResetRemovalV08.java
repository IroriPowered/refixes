package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.SharedInstanceResetV08;
import com.hypixel.hytale.builtin.instances.removal.RemovalSystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
@Mixin(RemovalSystem.class)
public class MixinSharedInstanceResetRemovalV08 {

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/builtin/instances/removal/InstanceDataResource;setRemoving(Z)V",
                            shift = At.Shift.AFTER),
            require = 1,
            allow = 1)
    private void refixes$armReset(float dt, int index, Store<ChunkStore> store, CallbackInfo ci) {
        SharedInstanceResetV08.onRemovalDecided(store.getExternalData().getWorld());
    }

    @WrapOperation(
            method = "lambda$removeAfterTick$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/Universe;removeWorldAsync(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private static CompletableFuture<Boolean> refixes$gateReset(
            Universe universe, String name, Operation<CompletableFuture<Boolean>> original) {
        return SharedInstanceResetV08.removeAsync(name, () -> original.call(universe, name));
    }
}
