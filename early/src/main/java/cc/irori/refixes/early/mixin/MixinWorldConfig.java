package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldConfig.class)
public class MixinWorldConfig {

    @Inject(
            method = "setSpawnProvider",
            at = @At("HEAD")
    )
    private void refixes$markConfigDirtyOnSetSpawnProvider(ISpawnProvider spawnProvider, CallbackInfo ci) {
        ((WorldConfig)(Object) this).markChanged();
    }
}
