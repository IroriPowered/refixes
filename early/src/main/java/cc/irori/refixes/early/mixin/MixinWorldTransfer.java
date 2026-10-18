package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.WorldPolicyTransfer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorldTransfer {
    @Inject(method = "onShutdown", at = @At("HEAD"))
    private void refixes$cancelTransfers(CallbackInfo ci) {
        WorldPolicyTransfer.closeWorld((World) (Object) this);
    }

    @WrapMethod(
            method =
                    "addPlayer(Lcom/hypixel/hytale/server/core/universe/PlayerRef;Lcom/hypixel/hytale/math/vector/Transform;Ljava/lang/Boolean;Ljava/lang/Boolean;)Ljava/util/concurrent/CompletableFuture;")
    private CompletableFuture<PlayerRef> refixes$transferPlayer(
            PlayerRef playerRef,
            Transform transform,
            Boolean clearWorldOverride,
            Boolean fadeInOutOverride,
            Operation<CompletableFuture<PlayerRef>> original) {
        Ref<EntityStore> initialReference = playerRef.getReference();
        if (initialReference == null) return original.call(playerRef, transform, clearWorldOverride, fadeInOutOverride);
        return WorldPolicyTransfer.awaitRemovalAndAttach(
                (World) (Object) this,
                playerRef,
                initialReference,
                () -> original.call(playerRef, transform, clearWorldOverride, fadeInOutOverride));
    }
}
