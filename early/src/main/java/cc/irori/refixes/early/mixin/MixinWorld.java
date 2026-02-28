package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinWorld {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Redirect(
            method =
                    "addPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/math/vector/Transform;Ljava/lang/Boolean;Ljava/lang/Boolean;)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/PlayerRef;getReference()Lcom/hypixel/hytale/component/Ref;"))
    private Ref<EntityStore> refixes$skipBuiltInReferenceCheck(PlayerRef instance) {
        return null;
    }

    @Inject(
            method =
                    "addPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/math/vector/Transform;Ljava/lang/Boolean;Ljava/lang/Boolean;)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/PlayerRef;getPacketHandler()Lcom/hypixel/hytale/server/core/io/PacketHandler;",
                            shift = At.Shift.BEFORE))
    private void refixes$tryResolveRaceCondition(
            PlayerRef playerRef,
            Transform transform,
            Boolean clearWorldOverride,
            Boolean fadeInOutOverride,
            CallbackInfoReturnable<CompletableFuture<PlayerRef>> cir) {
        if (playerRef.getReference() != null) {
            boolean resolved = false;
            for (int i = 0; i < 5; i++) {
                if (refixes$tryResolve(playerRef)) {
                    resolved = true;
                    break;
                }
            }

            if (!resolved) {
                throw new IllegalStateException("Player is already in a world");
            }
            refixes$LOGGER.atInfo().log("World#addPlayer(): Resolved player entity removal race condition");
        }
    }

    @Unique
    private static boolean refixes$tryResolve(PlayerRef playerRef) {
        try {
            Thread.sleep(20);
            if (playerRef.getReference() == null) {
                return true;
            }
        } catch (InterruptedException ignored) {
        }
        return false;
    }

    // Redirects the config save .join() in onShutdown() to use a timeout
    @Redirect(
            method = "onShutdown",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"))
    private Object refixes$configSaveJoinWithTimeout(CompletableFuture<?> future) {
        boolean wasInterrupted = Thread.interrupted();
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            refixes$LOGGER.atWarning().log("World#onShutdown(): Config save timed out after 10s, continuing shutdown");
            return null;
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log("World#onShutdown(): Config save failed, continuing shutdown");
            return null;
        } finally {
            if (wasInterrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
