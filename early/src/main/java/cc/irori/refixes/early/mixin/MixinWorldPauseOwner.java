package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.WorldPauseOwnerAccess;
import com.hypixel.hytale.server.core.universe.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorldPauseOwner implements WorldPauseOwnerAccess {

    @Unique
    private Object refixes$pauseOwner;

    @Inject(method = "setPaused", at = @At("HEAD"))
    private void refixes$clearPauseOwner(boolean paused, CallbackInfo ci) {
        refixes$pauseOwner = null;
    }

    @Override
    public void refixes$setPaused(boolean paused, Object owner) {
        ((World) (Object) this).setPaused(paused);
        refixes$pauseOwner = paused ? owner : null;
    }

    @Override
    public boolean refixes$isPausedBy(Object owner) {
        return owner != null && refixes$pauseOwner == owner && ((World) (Object) this).isPaused();
    }
}
