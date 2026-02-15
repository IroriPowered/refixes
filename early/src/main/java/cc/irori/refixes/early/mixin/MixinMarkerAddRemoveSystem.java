package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.reference.InvalidatablePersistentRef;
import com.hypixel.hytale.server.npc.systems.SpawnReferenceSystems;
import com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SpawnReferenceSystems.MarkerAddRemoveSystem.class)
public class MixinMarkerAddRemoveSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Redirect(
            method = "onEntityRemove",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hypixel/hytale/server/spawning/spawnmarkers/SpawnMarkerEntity;getNpcReferences()[Lcom/hypixel/hytale/server/core/entity/reference/InvalidatablePersistentRef;"
            )
    )
    public InvalidatablePersistentRef[] patches$fixOnEntityRemoveRedirect(SpawnMarkerEntity instance) {
        InvalidatablePersistentRef[] refs = instance.getNpcReferences();
        if (refs == null) {
            refixes$LOGGER.atWarning().log("MarkerAddRemoveSystem#onEntityRemove(): Fixed null NPC references (%s)", instance.getSpawnMarkerId());
            return new InvalidatablePersistentRef[0];
        }
        return refs;
    }
}
