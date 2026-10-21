package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SharedInstanceReset;
import com.hypixel.hytale.builtin.instances.removal.RemovalSystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RemovalSystem.class)
public class MixinSharedInstanceResetRemoval {

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
        SharedInstanceReset.onRemovalDecided(store.getExternalData().getWorld());
    }

    @WrapOperation(
            method = {
                "tick -> Ljava/lang/Runnable;run()V",
                "setupInstanceRemoval -> Ljava/lang/Runnable;run()V -> Ljava/lang/Runnable;run()V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/Universe;removeWorld(Ljava/lang/String;)Z"),
            require = 2,
            allow = 2)
    private static boolean refixes$gateReset(
            Universe universe, String name, Operation<Boolean> original, @Local(argsOnly = true) World world) {
        return SharedInstanceReset.remove(world, () -> original.call(universe, name));
    }
}
