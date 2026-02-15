package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldMapTracker.class)
public abstract class MixinWorldMapTracker {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @Shadow
    protected abstract void unloadImages(int chunkViewRadius, int playerChunkX, int playerChunkZ);

    @Inject(
            method = "unloadImages",
            at = @At("HEAD"),
            cancellable = true
    )
    private void refixes$wrapUnloadImages(int chunkViewRadius, int playerChunkX, int playerChunkZ, CallbackInfo ci) {
        if (refixes$WRAPPING.get()) {
            // Run the original method
            return;
        }

        refixes$WRAPPING.set(true);
        ci.cancel();
        try {
            unloadImages(chunkViewRadius, playerChunkX, playerChunkZ);
        } catch (NullPointerException e) {
            refixes$LOGGER.atWarning().withCause(e).log("WorldMapTracker#unloadImages(): Failed to run");
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
