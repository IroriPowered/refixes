package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.reference.InvalidatablePersistentRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.SpawnMarkerReference;
import com.hypixel.hytale.server.npc.systems.SpawnReferenceSystems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnReferenceSystems.MarkerAddRemoveSystem.class)
public abstract class MixinMarkerAddRemoveSystem {

    @Inject(method = "onEntityAdded", at = @At("HEAD"))
    private void refixes$dropForeignOnAdd(
            Ref<EntityStore> ref,
            AddReason reason,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci) {
        if (reason == AddReason.LOAD) refixes$dropForeignMarker(ref, store);
    }

    @Inject(method = "onEntityRemove", at = @At("HEAD"))
    private void refixes$dropForeignOnRemove(
            Ref<EntityStore> ref,
            RemoveReason reason,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci) {
        refixes$dropForeignMarker(ref, store);
    }

    @Unique
    private static void refixes$dropForeignMarker(Ref<EntityStore> ref, Store<EntityStore> store) {
        SpawnMarkerReference spawnReference = store.getComponent(ref, SpawnMarkerReference.getComponentType());
        if (spawnReference == null) return;
        InvalidatablePersistentRef reference = spawnReference.getReference();
        Ref<EntityStore> markerRef = reference.getEntity(store);
        if (markerRef != null && markerRef.getStore() != store) reference.setUuid(reference.getUuid());
    }
}
