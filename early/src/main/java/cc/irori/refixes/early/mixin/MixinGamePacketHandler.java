package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.player.ClientMovement;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GamePacketHandler.class)
public abstract class MixinGamePacketHandler {

    @Shadow
    protected abstract void lambda$handle$0(Ref<?> ref, Store<?> store, ClientMovement packet);

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @Inject(method = "lambda$handle$0", at = @At("HEAD"), cancellable = true)
    private void refixes$wrapClientMovementHandler(Ref<?> ref, Store<?> store, ClientMovement packet, CallbackInfo ci) {
        if (refixes$WRAPPING.get()) {
            // Run the original method
            return;
        }

        ci.cancel();
        refixes$WRAPPING.set(true);
        try {
            lambda$handle$0(ref, store, packet);
        } catch (NullPointerException e) {
            refixes$LOGGER.atWarning().withCause(e).log("GamePacketHandler#handle(ClientMovement): Failed to run");
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
