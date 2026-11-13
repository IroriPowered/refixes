package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SharedInstanceReset;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Universe.class)
public class MixinSharedInstanceResetUniverse {

    @WrapOperation(
            method = "lambda$removeWorld$0",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/hytale/server/core/universe/world/World;validateDeleteOnRemove()V"),
            require = 1,
            allow = 1)
    private void refixes$resetAfterStop(World world, Operation<Void> original) {
        SharedInstanceReset.deleteAfterStop(world, () -> original.call(world));
    }
}
