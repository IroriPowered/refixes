package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.reference.InvalidatablePersistentRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.SpawnMarkerReference;
import com.hypixel.hytale.server.npc.systems.SpawnReferenceSystems;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SpawnReferenceSystems.MarkerAddRemoveSystem.class)
public abstract class MixinMarkerAddRemoveSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @WrapMethod(method = "onEntityAdded")
    private void refixes$wrapOnEntityAdded(
            Ref<EntityStore> ref,
            AddReason reason,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            Operation<Void> original) {
        if (reason != AddReason.LOAD || refixes$WRAPPING.get()) {
            original.call(ref, reason, store, commandBuffer);
            return;
        }
        refixes$dropForeignMarker(ref, store);
        refixes$WRAPPING.set(true);
        try {
            original.call(ref, reason, store, commandBuffer);
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "MarkerAddRemoveSystem#onEntityAdded(): Failed to process spawn marker on load, discarding");
        } finally {
            refixes$WRAPPING.set(false);
        }
    }

    @WrapMethod(method = "onEntityRemove")
    private void refixes$wrapOnEntityRemove(
            Ref<EntityStore> ref,
            RemoveReason reason,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            Operation<Void> original) {
        if (refixes$WRAPPING.get()) {
            original.call(ref, reason, store, commandBuffer);
            return;
        }
        refixes$dropForeignMarker(ref, store);
        refixes$WRAPPING.set(true);
        try {
            original.call(ref, reason, store, commandBuffer);
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "MarkerAddRemoveSystem#onEntityRemove(): Unhandled exception while removing NPC references");
        } finally {
            refixes$WRAPPING.set(false);
        }
    }

    @Unique
    private static void refixes$dropForeignMarker(Ref<EntityStore> ref, Store<EntityStore> store) {
        SpawnMarkerReference spawnReference = store.getComponent(ref, SpawnMarkerReference.getComponentType());
        if (spawnReference == null) {
            return;
        }
        InvalidatablePersistentRef reference = spawnReference.getReference();
        Ref<EntityStore> markerRef = reference.getEntity(store);
        if (markerRef != null && markerRef.getStore() != store) {
            reference.setUuid(reference.getUuid());
        }
    }
}
