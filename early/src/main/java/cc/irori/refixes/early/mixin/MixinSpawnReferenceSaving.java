package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.SpawnMarkerReference;
import com.hypixel.hytale.server.npc.systems.SpawnReferenceSystems;
import com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerEntity;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpawnReferenceSystems.class)
public class MixinSpawnReferenceSaving {

    @Inject(
            method =
                    "disconnectFromMarker(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/component/ComponentType;Lcom/hypixel/hytale/component/ComponentType;Lcom/hypixel/hytale/component/ComponentType;Lcom/hypixel/hytale/component/ResourceType;)V",
            at = @At("TAIL"),
            require = 1)
    private static void refixes$saveDisconnectedMarker(
            Ref<EntityStore> ref,
            Store<EntityStore> store,
            ComponentType<EntityStore, SpawnMarkerReference> spawnReferenceComponentType,
            ComponentType<EntityStore, SpawnMarkerEntity> spawnMarkerEntityComponentType,
            ComponentType<EntityStore, UUIDComponent> uuidComponentComponentType,
            ResourceType<EntityStore, WorldTimeResource> worldTimeResourceResourceType,
            CallbackInfo ci,
            @Local(ordinal = 1) Ref<EntityStore> spawnMarkerRef) {
        Dirty dirty = store.getComponent(spawnMarkerRef, Dirty.getComponentType());
        if (dirty != null) dirty.forceMarkDirty();
    }

    @Inject(
            method = "removeNPCsForMarker(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/Store;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/spawning/spawnmarkers/SpawnMarkerEntity;completeSpawnedNpcRemoval(Ljava/time/Instant;)V",
                            shift = At.Shift.AFTER),
            require = 1,
            allow = 1)
    private static void refixes$saveClearedMarker(
            Ref<EntityStore> markerRef, Store<EntityStore> store, CallbackInfo ci) {
        Dirty dirty = store.getComponent(markerRef, Dirty.getComponentType());
        if (dirty != null) dirty.forceMarkDirty();
    }
}
