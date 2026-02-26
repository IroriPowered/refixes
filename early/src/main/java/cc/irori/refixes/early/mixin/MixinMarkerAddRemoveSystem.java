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
import com.hypixel.hytale.server.npc.systems.SpawnReferenceSystems;
import com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerEntity;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnReferenceSystems.MarkerAddRemoveSystem.class)
public abstract class MixinMarkerAddRemoveSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<InvalidatablePersistentRef[]> refixes$NPC_REFERENCES = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<UUID> refixes$REMOVED_UUID = new ThreadLocal<>();

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

    // Redirects getNpcReferences() to fix the AIOOBE crash
    @Redirect(
            method = "onEntityRemove",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/spawning/spawnmarkers/SpawnMarkerEntity;getNpcReferences()[Lcom/hypixel/hytale/server/core/entity/reference/InvalidatablePersistentRef;"))
    private InvalidatablePersistentRef[] refixes$storeAndFilterNpcReferences(SpawnMarkerEntity instance) {
        InvalidatablePersistentRef[] refs = instance.getNpcReferences();
        refixes$NPC_REFERENCES.set(refs);

        if (refs == null) {
            return null;
        }

        UUID removedUuid = refixes$REMOVED_UUID.get();
        if (removedUuid == null) {
            return refs;
        }

        // find and remove the entry matching the removed entity's UUID.
        int matchIndex = -1;
        for (int i = 0; i < refs.length; i++) {
            if (refs[i] != null && refs[i].getUuid().equals(removedUuid)) {
                matchIndex = i;
                break;
            }
        }

        if (matchIndex == -1) {
            // return a copy with one fewer element to match the allocation size.
            refixes$LOGGER.atWarning().log(
                    "MarkerAddRemoveSystem#onEntityRemove(): UUID %s not found in npcReferences (length=%d) for marker %s, "
                            + "returning truncated array to prevent AIOOBE",
                    removedUuid, refs.length, instance.getSpawnMarkerId());
            if (refs.length <= 1) {
                return new InvalidatablePersistentRef[0];
            }
            InvalidatablePersistentRef[] truncated = new InvalidatablePersistentRef[refs.length - 1];
            System.arraycopy(refs, 0, truncated, 0, truncated.length);
            return truncated;
        }

        // if uuid found, copy every element except the one with the matching uuid
        InvalidatablePersistentRef[] filtered = new InvalidatablePersistentRef[refs.length - 1];
        System.arraycopy(refs, 0, filtered, 0, matchIndex);
        System.arraycopy(refs, matchIndex + 1, filtered, matchIndex, refs.length - matchIndex - 1);
        return filtered;
    }

    /**
     * Captures the UUID of the entity being removed into a ThreadLocal,
     * to make it available in refixes$storeAndFilterNpcReferences
     */
    @Inject(
            method = "onEntityRemove",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/spawning/spawnmarkers/SpawnMarkerEntity;getNpcReferences()[Lcom/hypixel/hytale/server/core/entity/reference/InvalidatablePersistentRef;"))
    private void refixes$captureRemovedUuid(
            Ref<EntityStore> ref,
            RemoveReason reason,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci,
            @Local(name = "uuid") UUID uuid) {
        refixes$REMOVED_UUID.set(uuid);
    }

    @Inject(
            method = "onEntityRemove",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/spawning/spawnmarkers/SpawnMarkerEntity;getNpcReferences()[Lcom/hypixel/hytale/server/core/entity/reference/InvalidatablePersistentRef;",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void refixes$discardOnNullNpcReferences(
            Ref<EntityStore> ref,
            RemoveReason reason,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci,
            @Local(name = "spawnMarkerComponent") SpawnMarkerEntity spawnMarkerComponent) {
        InvalidatablePersistentRef[] refs = refixes$NPC_REFERENCES.get();
        refixes$NPC_REFERENCES.remove();
        refixes$REMOVED_UUID.remove();

        if (refs == null) {
            refixes$LOGGER.atWarning().log(
                    "MarkerAddRemoveSystem#onEntityRemove(): Discarding due to null NPC references (%s)",
                    spawnMarkerComponent.getSpawnMarkerId());
            ci.cancel();
        }
    }
}
