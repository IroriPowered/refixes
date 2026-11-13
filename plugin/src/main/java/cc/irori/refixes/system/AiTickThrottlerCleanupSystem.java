package cc.irori.refixes.system;

import cc.irori.refixes.component.TickThrottled;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.impl.TickThrottledCleanupConfig;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.StepComponent;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AiTickThrottlerCleanupSystem extends RefSystem<EntityStore> {
    @Override
    public void onEntityAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull AddReason addReason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (addReason != AddReason.LOAD) {
            return;
        }

        TickThrottledCleanupConfig cfg = TickThrottledCleanupConfig.get();
        ComponentType<EntityStore, TickThrottled> liveType = TickThrottled.getComponentType();
        TickThrottled current = liveType == null ? null : commandBuffer.getComponent(ref, liveType);
        if (current != null && current.isLive()) {
            return;
        }

        if (cfg.getValue(TickThrottledCleanupConfig.CLEANUP_FROZEN_ENTITIES)) {
            ComponentType<EntityStore, TickThrottled> tickThrottledType = TickThrottled.getComponentType();
            if (tickThrottledType != null && commandBuffer.getComponent(ref, tickThrottledType) != null) {
                if (!isNpcTypeExcluded(
                        ref, commandBuffer, cfg, TickThrottledCleanupConfig.CLEANUP_EXCLUDED_NPC_TYPES)) {
                    commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
                    commandBuffer.tryRemoveComponent(ref, StepComponent.getComponentType());
                    commandBuffer.tryRemoveComponent(ref, tickThrottledType);
                }
            }
        }

        if (cfg.getValue(TickThrottledCleanupConfig.LEGACY_CLEANUP)) {
            boolean hasOrphan = commandBuffer.getComponent(ref, Frozen.getComponentType()) != null
                    || commandBuffer.getComponent(ref, StepComponent.getComponentType()) != null;
            if (hasOrphan
                    && !isNpcTypeExcluded(
                            ref, commandBuffer, cfg, TickThrottledCleanupConfig.LEGACY_CLEANUP_EXCLUDED_NPC_TYPES)) {
                commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
                commandBuffer.tryRemoveComponent(ref, StepComponent.getComponentType());
                ComponentType<EntityStore, TickThrottled> tickThrottledType = TickThrottled.getComponentType();
                if (tickThrottledType != null) {
                    commandBuffer.tryRemoveComponent(ref, tickThrottledType);
                }
            }
        }
    }

    private static boolean isNpcTypeExcluded(
            Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            TickThrottledCleanupConfig cfg,
            ConfigurationKey<TickThrottledCleanupConfig, String[]> excludedTypesKey) {
        Set<String> excluded = new HashSet<>(Arrays.asList(cfg.getValue(excludedTypesKey)));
        if (excluded.isEmpty()) {
            return false;
        }
        ComponentType<EntityStore, NPCEntity> npcEntityType = NPCEntity.getComponentType();
        if (npcEntityType == null) {
            return false;
        }
        NPCEntity npcEntity = commandBuffer.getComponent(ref, npcEntityType);
        return npcEntity != null && excluded.contains(npcEntity.getNPCTypeId());
    }

    @Override
    public void onEntityRemove(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull RemoveReason removeReason,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // no-op
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return Query.not(Player.getComponentType());
    }
}
