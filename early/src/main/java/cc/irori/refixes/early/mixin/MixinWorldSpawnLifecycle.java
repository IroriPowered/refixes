package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.WorldPolicyTaskCoalescer;
import com.hypixel.hytale.server.core.universe.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(World.class)
public abstract class MixinWorldSpawnLifecycle {
    @Inject(method = "onShutdown", at = @At("HEAD"))
    private void refixes$closeSpawnTasks(CallbackInfo ci) {
        WorldPolicyTaskCoalescer.close(this);
    }
}
