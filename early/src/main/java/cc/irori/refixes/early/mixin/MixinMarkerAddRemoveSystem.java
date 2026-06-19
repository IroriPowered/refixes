package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.systems.SpawnReferenceSystems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnReferenceSystems.MarkerAddRemoveSystem.class)
public abstract class MixinMarkerAddRemoveSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @Shadow
    public abstract void onEntityAdded(
            Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer);

    @Shadow
    public abstract void onEntityRemove(
            Ref<EntityStore> ref,
            RemoveReason reason,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer);

    @Inject(method = "onEntityAdded", at = @At("HEAD"), cancellable = true)
    private void refixes$wrapOnEntityAdded(
            Ref<EntityStore> ref,
            AddReason reason,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci) {
        if (reason != AddReason.LOAD) {
            return;
        }
        if (refixes$WRAPPING.get()) {
            return;
        }
        ci.cancel();
        refixes$WRAPPING.set(true);
        try {
            onEntityAdded(ref, reason, store, commandBuffer);
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "MarkerAddRemoveSystem#onEntityAdded(): Failed to process spawn marker on load, discarding");
        } finally {
            refixes$WRAPPING.set(false);
        }
    }

    @Inject(method = "onEntityRemove", at = @At("HEAD"), cancellable = true)
    private void refixes$wrapOnEntityRemove(
            Ref<EntityStore> ref,
            RemoveReason reason,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci) {
        if (refixes$WRAPPING.get()) {
            return;
        }
        ci.cancel();
        refixes$WRAPPING.set(true);
        try {
            onEntityRemove(ref, reason, store, commandBuffer);
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "MarkerAddRemoveSystem#onEntityRemove(): Unhandled exception while removing NPC references");
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
