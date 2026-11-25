package cc.irori.refixes.service;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import java.nio.file.Path;

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
public final class ChunkLoaderServiceV08 extends ChunkLoaderService {

    public ChunkLoaderServiceV08(Path pluginDataDir) {
        super(pluginDataDir);
    }

    @Override
    void reconcile(Store<ChunkStore> store) {}

    @Override
    void keepColumn(Store<ChunkStore> store, Ref<ChunkStore> ref, WorldChunk column) {}

    @Override
    void requestSections(World world, WorldState state, long index, Hold hold) {
        for (int y = 0; y < ChunkUtil.HEIGHT_SECTIONS; y++) request(world, state, index, hold, y);
        for (int y : hold.registration.sections) {
            if (y < 0 || y >= ChunkUtil.HEIGHT_SECTIONS) request(world, state, index, hold, y);
        }
    }

    private void request(World world, WorldState state, long index, Hold hold, int y) {
        if (hold.sections.containsKey(y) || !hold.requested.add(y)) return;
        try {
            var future = world.getChunkStore()
                    .getChunkSectionReferenceAsync(
                            hold.registration.x, y, hold.registration.z, GetChunkFlags.SET_TICKING);
            future.whenComplete((ref, error) -> executeOnWorld(world, () -> {
                if (!current(world, state, index, hold)) return;
                if (error != null) {
                    hold.requested.remove(y);
                    failed(world, hold, error);
                    return;
                }
                if (ref == null) return;
                hold.requested.remove(y);
                if (!ref.isValid()) return;
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

    @Override
    boolean retains(ChunkData registration, int y) {
        return (y >= 0 && y < ChunkUtil.HEIGHT_SECTIONS) || registration.sections.contains(y);
    }

    @Override
    void sectionHeld(WorldState state, Hold hold, int y) {
        hold.requested.remove(y);
    }
}
