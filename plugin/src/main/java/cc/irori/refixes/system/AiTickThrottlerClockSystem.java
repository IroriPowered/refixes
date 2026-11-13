package cc.irori.refixes.system;

import cc.irori.refixes.service.AiTickThrottlerService;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.system.tick.RunWhenPausedSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;

public final class AiTickThrottlerClockSystem extends TickingSystem<EntityStore>
        implements RunWhenPausedSystem<EntityStore> {
    private final AiTickThrottlerService service;

    public AiTickThrottlerClockSystem(AiTickThrottlerService service) {
        this.service = service;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return RootDependency.firstSet();
    }

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<EntityStore> store) {
        service.advanceClock(store, dt);
    }
}
