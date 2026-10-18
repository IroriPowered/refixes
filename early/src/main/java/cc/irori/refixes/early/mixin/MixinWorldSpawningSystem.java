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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(WorldSpawningSystem.class)
public abstract class MixinWorldSpawningSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @WrapMethod(method = "pickRandomChunk")
    private Ref<ChunkStore> refixes$wrapPickRandomChunk(
            WorldEnvironmentSpawnData spawnData,
            WorldNPCSpawnStat stat,
            WorldSpawnData worldSpawnData,
            Store<ChunkStore> store,
            Operation<Ref<ChunkStore>> original) {
        if (refixes$WRAPPING.get()) {
            // Run the original method
            return original.call(spawnData, stat, worldSpawnData, store);
        }

        refixes$WRAPPING.set(true);
        try {
            return original.call(spawnData, stat, worldSpawnData, store);
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log("WorldSpawningSystem#pickRandomChunk(): Failed to run");
            return null;
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
