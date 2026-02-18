package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnReferenceSystems.MarkerAddRemoveSystem.class)
public class MixinMarkerAddRemoveSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<InvalidatablePersistentRef[]> refixes$NPC_REFERENCES = new ThreadLocal<>();

    @Redirect(
            method = "onEntityRemove",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/spawning/spawnmarkers/SpawnMarkerEntity;getNpcReferences()[Lcom/hypixel/hytale/server/core/entity/reference/InvalidatablePersistentRef;"))
    private InvalidatablePersistentRef[] refixes$storeNpcReferences(SpawnMarkerEntity instance) {
        InvalidatablePersistentRef[] refs = instance.getNpcReferences();
        refixes$NPC_REFERENCES.set(refs);
        return refs;
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

        if (refs == null) {
            refixes$LOGGER.atWarning().log(
                    "MarkerAddRemoveSystem#onEntityRemove(): Discarding due to null NPC references (%s)",
                    spawnMarkerComponent.getSpawnMarkerId());
            ci.cancel();
        }
    }
}
