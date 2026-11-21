package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.WorldAliveAccess;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Universe.class)
public abstract class MixinShutdownWorldRemoval {

    @Shadow
    private Map<UUID, World> worldsByUuid;

    @WrapOperation(
            method = "shutdownAllWorlds",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/universe/world/World;stop()V"),
            require = 1,
            allow = 1)
    private void refixes$removeWorldOnShutdown(World world, Operation<Void> original) {
        try {
            HytaleServer.get()
                    .getEventBus()
                    .dispatchFor(RemoveWorldEvent.class, world.getName())
                    .dispatch(new RemoveWorldEvent(world, RemoveWorldEvent.RemovalReason.GENERAL));
            ((WorldAliveAccess) world).refixes$getAlive().set(false);
            original.call(world);
            worldsByUuid.remove(world.getWorldConfig().getUuid(), world);
            world.validateDeleteOnRemove();
        } catch (RuntimeException failure) {
            Logs.logger().atSevere().withCause(failure).log("Failed to remove world %s on shutdown", world.getName());
        }
    }
}
