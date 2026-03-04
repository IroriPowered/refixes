package cc.irori.refixes.system;

import cc.irori.refixes.component.TickThrottled;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.impl.AiTickThrottlerConfig;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class AiTickThrottlerCleanupSystem extends RefSystem<EntityStore> {
    @Override
    public void onEntityAdded(
            @NonNull Ref<EntityStore> ref,
            @NonNull AddReason addReason,
            @NonNull Store<EntityStore> store,
            @NonNull CommandBuffer<EntityStore> commandBuffer) {
        if (addReason != AddReason.LOAD) {
            return;
        }

        AiTickThrottlerConfig cfg = AiTickThrottlerConfig.get();
        boolean throttlerEnabled = cfg.getValue(AiTickThrottlerConfig.ENABLED);

        if (cfg.getValue(AiTickThrottlerConfig.CLEANUP_FROZEN_ENTITIES)) {
            ComponentType<EntityStore, TickThrottled> tickThrottledType = TickThrottled.getComponentType();
            if (tickThrottledType != null && commandBuffer.getComponent(ref, tickThrottledType) != null) {
                if (!isNpcTypeExcluded(ref, commandBuffer, cfg, AiTickThrottlerConfig.CLEANUP_EXCLUDED_NPC_TYPES)) {
                    commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
                    commandBuffer.tryRemoveComponent(ref, StepComponent.getComponentType());
                    if (!throttlerEnabled) {
                        commandBuffer.tryRemoveComponent(ref, tickThrottledType);
                    }
                }
            }
        }

        if (cfg.getValue(AiTickThrottlerConfig.LEGACY_CLEANUP)) {
            boolean hasOrphan = commandBuffer.getComponent(ref, Frozen.getComponentType()) != null
                    || commandBuffer.getComponent(ref, StepComponent.getComponentType()) != null;
            if (hasOrphan
                    && !isNpcTypeExcluded(
                            ref, commandBuffer, cfg, AiTickThrottlerConfig.LEGACY_CLEANUP_EXCLUDED_NPC_TYPES)) {
                commandBuffer.tryRemoveComponent(ref, Frozen.getComponentType());
                commandBuffer.tryRemoveComponent(ref, StepComponent.getComponentType());
            }
        }
    }

    private static boolean isNpcTypeExcluded(
            Ref<EntityStore> ref,
            CommandBuffer<EntityStore> commandBuffer,
            AiTickThrottlerConfig cfg,
            ConfigurationKey<AiTickThrottlerConfig, String[]> excludedTypesKey) {
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
