package cc.irori.refixes.system;

import cc.irori.refixes.config.impl.EntityDespawnTimerConfig;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.projectile.component.Projectile;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Adds or overrides DespawnComponent timers for dropped items, block entities and projectiles.
 *
 * Default timers (configurable via EntityDespawnTimerConfig):
 * Items → 5 minutes
 * Block entities → 5 minutes
 * Projectiles → 1 minute
 * All other entity types are left untouched.
 */
public class EntityDespawnTimerSystem extends RefSystem<EntityStore> {

    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason addReason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        if (store.getComponent(ref, PreventPickup.getComponentType()) != null) {
            return;
        }

        EntityDespawnTimerConfig config = EntityDespawnTimerConfig.get();
        DespawnComponent existing = store.getComponent(ref, DespawnComponent.getComponentType());
        if (existing != null) {
            if (addReason == AddReason.LOAD || !config.getValue(EntityDespawnTimerConfig.OVERRIDE_EXISTING_TIMERS)) {
                return;
            }
            int seconds = resolveTimerSeconds(config, store, ref);
            if (seconds > 0) {
                commandBuffer.putComponent(ref, DespawnComponent.getComponentType(), despawnIn(commandBuffer, seconds));
            } else {
                commandBuffer.removeComponent(ref, DespawnComponent.getComponentType());
            }
            return;
        }

        if (addReason == AddReason.LOAD || !config.getValue(EntityDespawnTimerConfig.ADD_TIMER_WHEN_MISSING)) {
            return;
        }
        int seconds = resolveTimerSeconds(config, store, ref);
        if (seconds > 0) {
            commandBuffer.putComponent(ref, DespawnComponent.getComponentType(), despawnIn(commandBuffer, seconds));
        }
    }

    private static DespawnComponent despawnIn(CommandBuffer<EntityStore> commandBuffer, int seconds) {
        TimeResource time = commandBuffer.getResource(TimeResource.getResourceType());
        return DespawnComponent.despawnInSeconds(time, seconds);
    }

    private int resolveTimerSeconds(EntityDespawnTimerConfig config, Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store.getComponent(ref, ItemComponent.getComponentType()) != null) {
            return config.getValue(EntityDespawnTimerConfig.ITEM_DESPAWN_SECONDS);
        }

        if (store.getComponent(ref, BlockEntity.getComponentType()) != null) {
            return config.getValue(EntityDespawnTimerConfig.BLOCK_ENTITY_DESPAWN_SECONDS);
        }

        if (store.getComponent(ref, ProjectileComponent.getComponentType()) != null
                || store.getComponent(ref, Projectile.getComponentType()) != null) {
            return config.getValue(EntityDespawnTimerConfig.PROJECTILE_DESPAWN_SECONDS);
        }

        return -1;
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason removeReason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {}

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Query.or(
                ItemComponent.getComponentType(),
                BlockEntity.getComponentType(),
                ProjectileComponent.getComponentType(),
                Projectile.getComponentType());
    }
}
