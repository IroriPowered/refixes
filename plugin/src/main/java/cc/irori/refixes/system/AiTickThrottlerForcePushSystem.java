package cc.irori.refixes.system;

import cc.irori.refixes.component.TickThrottled;
import cc.irori.refixes.service.AiTickThrottlerService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.NPCPreTickSystem;
import com.hypixel.hytale.server.npc.systems.NPCVelocityInstructionSystem;
import com.hypixel.hytale.server.npc.systems.RoleSystems;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class AiTickThrottlerForcePushSystem extends EntityTickingSystem<EntityStore> {
    private final AiTickThrottlerService service;
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
            new SystemDependency<>(Order.AFTER, NPCVelocityInstructionSystem.class),
            new SystemDependency<>(Order.BEFORE, NPCPreTickSystem.class),
            new SystemDependency<>(Order.BEFORE, RoleSystems.MarkedTargetRebindSystem.class));

    public AiTickThrottlerForcePushSystem(AiTickThrottlerService service) {
        this.service = service;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        service.releaseForcePushed(dt, index, archetypeChunk, store, commandBuffer);
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType(), Frozen.getComponentType(), TickThrottled.getComponentType());
    }
}
