package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.portals.systems.PortalInvalidDestinationSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PortalInvalidDestinationSystem.class)
public class MixinPortalInvalidDestinationSystem {

    @WrapMethod(
            method =
                    "turnOffPortalsInWorld(Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/server/core/universe/world/World;)V")
    private static void refixes$skipClosedOrigin(World originWorld, World destinationWorld, Operation<Void> original) {
        if (originWorld.getChunkStore().getStore().isShutdown()) return;
        original.call(originWorld, destinationWorld);
    }
}
