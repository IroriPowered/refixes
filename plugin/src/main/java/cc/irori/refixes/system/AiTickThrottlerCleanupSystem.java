package cc.irori.refixes.system;

import cc.irori.refixes.component.TickThrottled;
import cc.irori.refixes.config.impl.AiTickThrottlerConfig;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.StepComponent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class AiTickThrottlerCleanupSystem extends RefSystem<EntityStore> {

    private static final Query<EntityStore> QUERY =
            Query.and(EntityModule.get().getNPCMarkerComponentType(), TransformComponent.getComponentType());

    @Override
    public void onEntityAdded(
            @NonNull Ref<EntityStore> ref,
            @NonNull AddReason addReason,
            @NonNull Store<EntityStore> store,
            @NonNull CommandBuffer<EntityStore> commandBuffer) {
        if (!AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.CLEANUP_FROZEN_ENTITIES)) {
            return;
        }
        if (addReason != AddReason.LOAD) {
            return;
        }

        boolean sweep;
        if (AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.LEGACY_CLEANUP)) {
            sweep = commandBuffer.getComponent(ref, TickThrottled.getComponentType()) != null
                    || commandBuffer.getComponent(ref, Frozen.getComponentType()) != null
                    || commandBuffer.getComponent(ref, StepComponent.getComponentType()) != null;
        } else {
            sweep = commandBuffer.getComponent(ref, TickThrottled.getComponentType()) != null;
        }

        if (sweep) {
            commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
            commandBuffer.tryRemoveComponent(ref, StepComponent.getComponentType());
            commandBuffer.tryRemoveComponent(ref, TickThrottled.getComponentType());
        }
    }

    @Override
    public void onEntityRemove(
            @NonNull Ref<EntityStore> ref,
            @NonNull RemoveReason removeReason,
            @NonNull Store<EntityStore> store,
            @NonNull CommandBuffer<EntityStore> commandBuffer) {
        // no-op
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return Query.not(Player.getComponentType());
    }
}
