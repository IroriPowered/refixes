package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SharedInstanceReset;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Universe.class)
public class MixinSharedInstanceResetUniverse {

    @WrapOperation(
            method = "removeWorld(Ljava/lang/String;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/util/Map;remove(Ljava/lang/Object;)Ljava/lang/Object;",
                            ordinal = 0),
            require = 1,
            allow = 1)
    private Object refixes$resetBeforeUnregister(
            Map<String, World> worlds, Object name, Operation<Object> original, @Local World world) {
        SharedInstanceReset.resetBeforeUnregister(world);
        return original.call(worlds, name);
    }
}
