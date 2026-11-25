package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.builtin.portals.systems.CloseWorldWhenBreakingDeviceSystems;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(CloseWorldWhenBreakingDeviceSystems.class)
public abstract class MixinPortalDeviceWorldClose {

    @WrapOperation(
            method = "maybeCloseFragmentWorld(Lcom/hypixel/hytale/builtin/portals/components/PortalDevice;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/Universe;removeWorld(Ljava/lang/String;)Z"),
            require = 1,
            allow = 1)
    private static boolean refixes$removeOffThread(
            Universe universe, String name, Operation<Boolean> original, @Local World world) {
        CompletableFuture.runAsync(() -> {
                    if (universe.getWorld(name) == world) original.call(universe, name);
                })
                .exceptionally(error -> {
                    Logs.logger()
                            .atSevere()
                            .withCause(error)
                            .log("Failed to close portal world '%s' after its device was removed", name);
                    return null;
                });
        return true;
    }
}
