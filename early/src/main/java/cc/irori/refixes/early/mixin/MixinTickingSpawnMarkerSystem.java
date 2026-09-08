package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.SpawnMarkerReference;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import com.hypixel.hytale.server.npc.systems.SpawnReferenceSystems;
import com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SpawnReferenceSystems.TickingSpawnMarkerSystem.class)
public class MixinTickingSpawnMarkerSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Shadow
    @Final
    private ComponentType<EntityStore, SpawnMarkerReference> spawnReferenceComponentType;

    @Shadow
    @Final
    private ComponentType<EntityStore, SpawnMarkerEntity> markerTypeComponentType;

    @Shadow
    @Final
    private ComponentType<EntityStore, NPCEntity> npcEntityComponentType;

    @Overwrite
    public void tick(
            float dt,
            int index,
            ArchetypeChunk<EntityStore> archetypeChunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        NPCEntity npcComponent = archetypeChunk.getComponent(index, npcEntityComponentType);
        assert npcComponent != null;
        if (npcComponent.isDespawning() || npcComponent.isPlayingDespawnAnim()) {
            return;
        }
        SpawnMarkerReference spawnReference = archetypeChunk.getComponent(index, spawnReferenceComponentType);
        assert spawnReference != null;
        if (!spawnReference.tickMarkerLostTimeoutCounter(dt)) {
            return;
        }
        Ref<EntityStore> markerRef = spawnReference.getReference().getEntity(commandBuffer);
        if (markerRef != null) {
            SpawnMarkerEntity marker = markerRef.isValid() && markerRef.getStore() == commandBuffer.getStore()
                    ? commandBuffer.getComponent(markerRef, markerTypeComponentType)
                    : null;
            if (marker != null) {
                spawnReference.refreshTimeoutCounter();
                marker.refreshTimeout();
                return;
            }
        } else if (StateSupport.get(archetypeChunk.getReferenceTo(index), commandBuffer)
                .isInBusyState()) {
            spawnReference.refreshTimeoutCounter();
            return;
        }
        npcComponent.setToDespawn();
        refixes$LOGGER.atWarning().log(
                "NPCEntity despawning due to lost marker: %s", archetypeChunk.getReferenceTo(index));
    }
}
