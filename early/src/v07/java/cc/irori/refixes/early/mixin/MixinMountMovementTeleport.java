package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.entities.MountMovement;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.modules.entity.teleport.PendingTeleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@IfHytale("<0.8.0-pre.1")
@Mixin(GamePacketHandler.class)
public abstract class MixinMountMovementTeleport {

    @Inject(
            method =
                    "handleMountMovement(Lcom/hypixel/hytale/protocol/packets/entities/MountMovement;Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/component/Store;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            allow = 1)
    private void refixes$ignoreBeforeTeleportAck(
            MountMovement packet,
            PlayerRef playerRef,
            Ref<EntityStore> ref,
            World world,
            Store<EntityStore> store,
            CallbackInfo ci) {
        if (store.getArchetype(ref).contains(PendingTeleport.getComponentType())) ci.cancel();
    }
}
