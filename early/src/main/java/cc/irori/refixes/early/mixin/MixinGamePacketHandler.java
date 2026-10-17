package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GamePacketHandler.class)
public abstract class MixinGamePacketHandler {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @WrapMethod(
            method =
                    "handle(Lcom/hypixel/hytale/protocol/packets/player/ClientMovement;)V -> Ljava/lang/Runnable;run()V")
    private void refixes$wrapClientMovementHandler(
            Ref<?> ref,
            Store<?> store,
            ClientMovement packet,
            boolean ackLeftAcksPending,
            Vector3d ackedPosition,
            Operation<Void> original) {
        try {
            original.call(ref, store, packet, ackLeftAcksPending, ackedPosition);
        } catch (NullPointerException e) {
            refixes$LOGGER.atWarning().withCause(e).log("GamePacketHandler#handle(ClientMovement): Failed to run");
        }
    }
}
