package cc.irori.refixes.early.copychunks;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.BufferChunkSaver;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class ChunkClipboardOps {

    // Time the tick-saver gets to enqueue saves triggered by our remove() before we flush.
    private static final long FLUSH_DELAY_MS = 500L;

    private ChunkClipboardOps() {}

    /**
     * Force-unloads any currently-loaded chunks in {@code coords}, then asynchronously waits long
     * enough for the world's auto-save tick to enqueue their saves and flushes the buffer chunk
     * saver. Caller MUST invoke from the world's ticking thread.
     */
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
        return CompletableFuture.runAsync(
                () -> {
                    try {
                        saver.flush();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                CompletableFuture.delayedExecutor(FLUSH_DELAY_MS, TimeUnit.MILLISECONDS));
    }

    public static String formatBlockerMessage(String label, List<int[]> blockers) {
        StringBuilder sb = new StringBuilder(label).append(": ");
        for (int i = 0; i < blockers.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("(")
                    .append(blockers.get(i)[0])
                    .append(", ")
                    .append(blockers.get(i)[1])
                    .append(")");
        }
        return sb.toString();
    }
}
