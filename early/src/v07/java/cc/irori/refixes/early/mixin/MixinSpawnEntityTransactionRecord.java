package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.builtin.adventure.objectives.transaction.SpawnEntityTransactionRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@IfHytale("<0.8.0-pre.1")
@Mixin(SpawnEntityTransactionRecord.class)
public abstract class MixinSpawnEntityTransactionRecord {

    @Shadow
    protected UUID worldUUID;

    @Shadow
    protected UUID entityUUID;

    /**
     * @author Refixes
     * @reason Skip unloaded worlds and remove the entity on its world thread
     */
    @Overwrite
    private void removeEntity() {
        World world = Universe.get().getWorld(worldUUID);
        if (world == null) return;
        UUID entity = entityUUID;
        world.execute(() -> {
            Ref<EntityStore> ref = world.getEntityRef(entity);
            if (ref == null || !ref.isValid()) return;
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
        });
    }
}
