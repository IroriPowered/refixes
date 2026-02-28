package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entity.system.HideEntitySystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fixes "java.lang.IllegalStateException: Invalid entity reference!"
// in HideEntitySystems$AdventurePlayerSystem.tick() when a player teleports
// between worlds and their entity reference is invalidated mid-tick.

@Mixin(HideEntitySystems.AdventurePlayerSystem.class)
public abstract class MixinHideEntitySystems {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @Shadow
    public abstract void tick(
            float deltaTime,
            int entityIndex,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer);

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void refixes$wrapTick(
            float deltaTime,
            int entityIndex,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci) {
        if (refixes$WRAPPING.get()) {
            return;
        }

        ci.cancel();
        refixes$WRAPPING.set(true);
        try {
            tick(deltaTime, entityIndex, chunk, store, commandBuffer);
        } catch (IllegalStateException e) {
            refixes$LOGGER.atWarning().log(
                    "HideEntitySystems$AdventurePlayerSystem.tick(): Skipping tick for invalid entity reference (likely mid-teleport), discarding");
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
