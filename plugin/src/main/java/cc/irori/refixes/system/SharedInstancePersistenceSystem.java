package cc.irori.refixes.system;

import cc.irori.refixes.early.util.SharedInstanceConstants;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.NonNull;

public class SharedInstancePersistenceSystem extends TickingSystem<ChunkStore> {

    private static final HytaleLogger LOGGER = Logs.logger();

    @Override
    public void tick(float dt, int index, @NonNull Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        if (!world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
            return;
        }

        WorldConfig config = world.getWorldConfig();

        boolean changed = false;
        if (config.isDeleteOnRemove()) {
            config.setDeleteOnRemove(false);
            changed = true;
        }
        if (config.isDeleteOnUniverseStart()) {
            config.setDeleteOnUniverseStart(false);
            changed = true;
        }

        if (changed) {
            config.markChanged();
            LOGGER.atInfo().log("Set persistence flags for shared instance world '%s'", world.getName());
        }
    }
}
