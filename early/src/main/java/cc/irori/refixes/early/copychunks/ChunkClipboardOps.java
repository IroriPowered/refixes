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

    public static CompletableFuture<Void> forceUnloadAndDrain(
            World world, ChunkStore chunkStore, BufferChunkSaver saver, List<int[]> coords) {
        for (int[] coord : coords) {
            long idx = ChunkUtil.indexChunk(coord[0], coord[1]);
            Ref<ChunkStore> ref = chunkStore.getChunkReference(idx);
            if (ref != null) {
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
