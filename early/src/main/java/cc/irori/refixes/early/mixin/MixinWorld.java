package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.PathfindingBudget;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class MixinWorld {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Shadow
    @Final
    private EntityStore entityStore;

    @Inject(method = "getPlayers()Ljava/util/List;", at = @At("HEAD"), cancellable = true)
    private void refixes$nullGuardGetPlayers(CallbackInfoReturnable<List<Player>> cir) {
        if (this.entityStore == null || this.entityStore.getStore() == null) {
            cir.setReturnValue(Collections.emptyList());
        }
    }

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

    @Inject(method = "tick(F)V", at = @At("HEAD"))
    private void refixes$resetPathfindingBudget(float dt, CallbackInfo ci) {
        if (this.entityStore == null) {
            return;
        }
        Store<EntityStore> store = this.entityStore.getStore();
        if (store != null) {
            PathfindingBudget.reset(
                    store,
                    EarlyOptions.PATHFINDING_MAX_NEW_SEARCHES_PER_TICK.get(),
                    EarlyOptions.PATHFINDING_MAX_NODE_EXPANSIONS_PER_TICK.get());
        }
    }

    @Inject(method = "onShutdown", at = @At("HEAD"))
    private void refixes$dropPathfindingBudget(CallbackInfo ci) {
        if (this.entityStore != null) {
            PathfindingBudget.remove(this.entityStore.getStore());
        }
    }
}
