package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(WorldMapTracker.class)
public abstract class MixinWorldMapTracker {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @WrapMethod(method = "unloadImages")
    private void refixes$wrapUnloadImages(
            int chunkViewRadius, int playerChunkX, int playerChunkZ, Operation<Void> original) {
        if (refixes$WRAPPING.get()) {
            // Run the original method
            original.call(chunkViewRadius, playerChunkX, playerChunkZ);
            return;
        }

        refixes$WRAPPING.set(true);
        try {
            original.call(chunkViewRadius, playerChunkX, playerChunkZ);
        } catch (NullPointerException e) {
            refixes$LOGGER.atWarning().withCause(e).log("WorldMapTracker#unloadImages(): Failed to run");
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
