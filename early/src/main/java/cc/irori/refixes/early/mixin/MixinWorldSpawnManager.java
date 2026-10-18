package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.WorldPolicyTaskCoalescer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.spawning.world.manager.WorldSpawnManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSpawnManager.class)
public abstract class MixinWorldSpawnManager {
    @Shadow
    private static void onEnvironmentChanged(World world) {
        throw new AssertionError();
    }

    @Inject(method = "onEnvironmentChanged()V", at = @At("HEAD"), cancellable = true)
    private static void refixes$coalesceEnvRecalc(CallbackInfo ci) {
        ci.cancel();
        Universe.get().getWorlds().forEach((name, world) -> {
            if (!world.getWorldConfig().isSpawningNPC()) return;
            WorldPolicyTaskCoalescer.submit(world, world, () -> onEnvironmentChanged(world));
        });
    }
}
