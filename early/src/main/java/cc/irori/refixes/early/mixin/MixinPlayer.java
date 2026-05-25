package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fixes PlayerReadyEvent being dispatched on the Scheduler thread instead of the World thread.

@Mixin(Player.class)
public abstract class MixinPlayer {

    @Shadow
    public abstract void handleClientReady(boolean forced);

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Inject(method = "handleClientReady", at = @At("HEAD"), cancellable = true)
    private void refixes$redirectToWorldThread(boolean forced, CallbackInfo ci) {
        World world = ((Entity) (Object) this).getWorld();
        if (world == null) {
            return;
        }
        if (!world.isInThread()) {
            ci.cancel();
            if (world.isAlive()) {
                world.execute(() -> handleClientReady(forced));
            } else {
                refixes$LOGGER.atWarning().log(
                        "Player#handleClientReady(): World %s is not alive, discarding event", world.getName());
            }
        }
    }
}
