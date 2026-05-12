package cc.irori.refixes.early.copychunks;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.BufferChunkSaver;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ChunkClipboardOps {

    private ChunkClipboardOps() {}

    /**
     * Force-unloads any currently-loaded chunks in {@code coords}, waits for all in-flight saves
     * captured at unload-time to drain (so they can't race with our subsequent writes), then
     * flushes the buffer chunk saver's region files. Caller MUST invoke from the world's ticking
     * thread.
     */
    public static CompletableFuture<Void> forceUnloadAndDrain(
            World world, ChunkStore chunkStore, BufferChunkSaver saver, List<int[]> coords) {
        for (int[] coord : coords) {
            long idx = ChunkUtil.indexChunk(coord[0], coord[1]);
            Ref<ChunkStore> ref = chunkStore.getChunkReference(idx);
            if (ref != null) {
                // remove() also invalidates the Ref so any queued auto-save no-ops (verified in
                // ChunkSavingSystems#saveChunk: !reference.isValid() => log+skip).
                chunkStore.remove(ref, RemoveReason.UNLOAD);
                world.getNotificationHandler().updateChunk(idx);
            }
        }
        ChunkSavingSystems.Data savingData =
                (ChunkSavingSystems.Data) chunkStore.getStore().getResource(ChunkStore.SAVE_RESOURCE);
        return savingData.waitForSavingChunks().thenRunAsync(() -> {
            try {
                saver.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
