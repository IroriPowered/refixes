package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.spawning.world.manager.WorldSpawnManager;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldSpawnManager.class)
public abstract class MixinWorldSpawnManager {

    @Unique
    private static final Set<World> refixes$recalcPending = ConcurrentHashMap.newKeySet();

    @Shadow
    private static void onEnvironmentChanged(World world) {
        throw new AssertionError();
    }

    @Inject(method = "onEnvironmentChanged()V", at = @At("HEAD"), cancellable = true)
    private static void refixes$coalesceEnvRecalc(CallbackInfo ci) {
        ci.cancel();
        Universe.get().getWorlds().forEach((name, world) -> {
            if (!world.getWorldConfig().isSpawningNPC()) {
                return;
            }
            if (refixes$recalcPending.add(world)) {
                world.execute(() -> {
                    refixes$recalcPending.remove(world);
                    onEnvironmentChanged(world);
                });
            }
        });
    }
}
