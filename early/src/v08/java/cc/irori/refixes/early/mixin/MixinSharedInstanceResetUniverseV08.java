package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.SharedInstanceResetV08;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
@Mixin(Universe.class)
public class MixinSharedInstanceResetUniverseV08 {

    @WrapOperation(
            method = "lambda$removeWorld$0",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/hytale/server/core/universe/world/World;validateDeleteOnRemove()V"),
            require = 1,
            allow = 1)
    private void refixes$resetAfterStop(World world, Operation<Void> original) {
        SharedInstanceResetV08.deleteAfterStop(world, () -> original.call(world));
    }
}
