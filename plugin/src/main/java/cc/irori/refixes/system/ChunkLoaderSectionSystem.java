package cc.irori.refixes.system;

import cc.irori.refixes.service.ChunkLoaderService;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.RunWhenPausedSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.SectionUnloadingSystem;
import java.util.Set;
import org.jspecify.annotations.NonNull;

public final class ChunkLoaderSectionSystem extends TickingSystem<ChunkStore>
        implements RunWhenPausedSystem<ChunkStore> {
    private final ChunkLoaderService service;
    private final Set<Dependency<ChunkStore>> dependencies =
            Set.of(new SystemDependency<>(Order.BEFORE, SectionUnloadingSystem.class));

    public ChunkLoaderSectionSystem(ChunkLoaderService service) {
        this.service = service;
    }

    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int systemIndex, @NonNull Store<ChunkStore> store) {
        service.retainSections(store);
    }
}
