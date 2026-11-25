package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.accessor.SharedInstanceResetAccess;
import cc.irori.refixes.early.util.SharedInstanceResetV08;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
@Mixin(World.class)
public class MixinSharedInstanceResetWorldV08 implements SharedInstanceResetAccess {

    @Unique
    private volatile boolean refixes$visited;

    @Unique
    private volatile boolean refixes$resetArmed;

    @Unique
    private final AtomicReference<CompletableFuture<Void>> refixes$resetGate = new AtomicReference<>();

    @Inject(method = "trackPlayerRef", at = @At("HEAD"))
    private void refixes$markVisited(PlayerRef playerRef, CallbackInfo ci) {
        refixes$resetArmed = false;
        refixes$visited = true;
    }

    @WrapMethod(method = "onShutdown")
    private void refixes$releaseUnclaimedReset(Operation<Void> original) {
        try {
            original.call();
        } finally {
            SharedInstanceResetV08.release((World) (Object) this);
        }
    }

    @Override
    public boolean refixes$isVisited() {
        return refixes$visited;
    }

    @Override
    public void refixes$setVisited(boolean visited) {
        refixes$visited = visited;
    }

    @Override
    public boolean refixes$isResetArmed() {
        return refixes$resetArmed;
    }

    @Override
    public void refixes$setResetArmed(boolean armed) {
        refixes$resetArmed = armed;
    }

    @Override
    public AtomicReference<CompletableFuture<Void>> refixes$resetGate() {
        return refixes$resetGate;
    }
}
