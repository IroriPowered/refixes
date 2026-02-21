package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.spawning.world.WorldEnvironmentSpawnData;
import com.hypixel.hytale.server.spawning.world.WorldNPCSpawnStat;
import com.hypixel.hytale.server.spawning.world.component.WorldSpawnData;
import com.hypixel.hytale.server.spawning.world.system.WorldSpawningSystem;
import javax.annotation.Nullable;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldSpawningSystem.class)
public abstract class MixinWorldSpawningSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @Shadow
    @Nullable
    protected abstract Ref<ChunkStore> pickRandomChunk(
            @NonNullDecl WorldEnvironmentSpawnData spawnData,
            @NonNullDecl WorldNPCSpawnStat stat,
            @NonNullDecl WorldSpawnData worldSpawnData,
            @NonNullDecl Store<ChunkStore> store);

    @Inject(method = "pickRandomChunk", at = @At("HEAD"), cancellable = true)
    private void refixes$wrapPickRandomChunk(
            WorldEnvironmentSpawnData spawnData,
            WorldNPCSpawnStat stat,
            WorldSpawnData worldSpawnData,
            Store<ChunkStore> store,
            CallbackInfoReturnable<Ref<ChunkStore>> cir) {
        if (refixes$WRAPPING.get()) {
            // Run the original method
            return;
        }

        cir.cancel();
        refixes$WRAPPING.set(true);
        try {
            cir.setReturnValue(pickRandomChunk(spawnData, stat, worldSpawnData, store));
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log("WorldSpawningSystem#pickRandomChunk(): Failed to run");
            cir.setReturnValue(null);
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
