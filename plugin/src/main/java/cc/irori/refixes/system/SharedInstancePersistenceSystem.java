package cc.irori.refixes.system;

import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.builtin.instances.removal.RemovalSystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.system.tick.RunWhenPausedSystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.Set;
import javax.annotation.Nonnull;

public class SharedInstancePersistenceSystem extends TickingSystem<ChunkStore>
        implements RunWhenPausedSystem<ChunkStore> {
    private final Set<Dependency<ChunkStore>> dependencies =
            Set.of(new SystemDependency<>(Order.AFTER, RemovalSystem.class));

    @Override
    public Set<Dependency<ChunkStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float dt, int index, @Nonnull Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        if (!world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
            return;
        }

        preserveStorage(world.getWorldConfig());
    }

    public static void preserveStorage(WorldConfig config) {
        boolean changed = false;
        if (config.isDeleteOnRemove()) {
            config.setDeleteOnRemove(false);
            changed = true;
        }
        if (config.isDeleteOnUniverseStart()) {
            config.setDeleteOnUniverseStart(false);
            changed = true;
        }
        if (changed) config.markChanged();
    }
}
