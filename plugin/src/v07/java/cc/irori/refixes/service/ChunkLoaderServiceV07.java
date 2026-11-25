package cc.irori.refixes.service;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import java.nio.file.Path;
import java.util.List;

@IfHytale("<0.8.0-pre.1")
public final class ChunkLoaderServiceV07 extends ChunkLoaderService {

    public ChunkLoaderServiceV07(Path pluginDataDir) {
        super(pluginDataDir);
    }

    @Override
    void reconcile(Store<ChunkStore> store) {
        store.forEachChunk(ChunkSection.getComponentType(), (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                sectionAdded(store, chunk.getReferenceTo(i), chunk.getComponent(i, ChunkSection.getComponentType()));
            }
        });
    }

    @Override
    void keepColumn(Store<ChunkStore> store, Ref<ChunkStore> ref, WorldChunk column) {
        column.resetActiveTimer();
        store.tryRemoveComponent(ref, ChunkStore.REGISTRY.getNonTickingComponentType());
    }

    @Override
    void requestSections(World world, WorldState state, long index, Hold hold) {
        for (int y : List.copyOf(hold.registration.sections)) {
            if (hold.sections.containsKey(y) || !hold.requested.add(y)) continue;
            try {
                var future = world.getChunkStore()
                        .getChunkSectionReferenceAsync(
                                hold.registration.x, y, hold.registration.z, GetChunkFlags.SET_TICKING);
                future.whenComplete((ref, error) -> executeOnWorld(world, () -> {
                    if (!current(world, state, index, hold)) return;
                    hold.requested.remove(y);
                    if (error != null || ref == null || !ref.isValid()) {
                        failed(world, hold, error);
                        return;
                    }
                    ChunkSection section =
                            world.getChunkStore().getStore().getComponent(ref, ChunkSection.getComponentType());
                    if (section == null) failed(world, hold, null);
                    else sectionAdded(world.getChunkStore().getStore(), ref, section);
                }));
            } catch (RuntimeException e) {
                hold.requested.remove(y);
                failed(world, hold, e);
            }
        }
    }

    @Override
    boolean retains(ChunkData registration, int y) {
        return true;
    }

    @Override
    void sectionHeld(WorldState state, Hold hold, int y) {
        if (hold.registration.sections.add(y)) state.dirty = true;
    }
}
