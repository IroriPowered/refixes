package cc.irori.refixes.system;

import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.WorldEventSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkSaveEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.jspecify.annotations.NonNull;

public class SharedInstanceChunkSaveSkipSystem extends WorldEventSystem<ChunkStore, ChunkSaveEvent> {

    public SharedInstanceChunkSaveSkipSystem() {
        super(ChunkSaveEvent.class);
    }

    @Override
    public void handle(
            @NonNull Store<ChunkStore> store,
            @NonNull CommandBuffer<ChunkStore> commandBuffer,
            @NonNull ChunkSaveEvent event) {
        WorldChunk chunk = event.getChunk();
        if (!chunk.is(ChunkFlag.ON_DISK) || chunk.is(ChunkFlag.NEEDS_FORMAT_REWRITE)) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (!world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
            return;
        }
        chunk.consumeNeedsSaving();
        event.setCancelled(true);
    }
}
