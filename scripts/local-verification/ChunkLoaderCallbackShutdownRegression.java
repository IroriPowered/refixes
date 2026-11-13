package cc.irori.refixes.service;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ChunkLoaderCallbackShutdownRegression {
    public static void main(String[] args) throws Exception {
        ChunkLoaderService service = new ChunkLoaderService(Files.createTempDirectory("retained-callback-shutdown"));
        World world = new World("callback-shutdown");
        service.addChunk(world, 1, 2, "pending", 0);
        world.drain();
        CountDownLatch acquiring = new CountDownLatch(1);
        CountDownLatch finishAcquire = new CountDownLatch(1);
        WorldChunk column = new WorldChunk() {
            @Override
            public void addKeepLoaded() {
                super.addKeepLoaded();
                acquiring.countDown();
                try {
                    if (!finishAcquire.await(5, TimeUnit.SECONDS)) throw new AssertionError("acquisition never resumed");
                } catch (InterruptedException error) {
                    throw new AssertionError(error);
                }
            }
        };
        Ref<ChunkStore> ref = new Ref<ChunkStore>().put(WorldChunk.getComponentType(), column);
        CompletableFuture<Void> completion = CompletableFuture.runAsync(() -> world.onOwner(() -> world.getChunkStore().columns.getFirst().complete(ref)));
        require(acquiring.await(5, TimeUnit.SECONDS), "native acquisition never started");
        CountDownLatch shutdownStarted = new CountDownLatch(1);
        CompletableFuture<Void> shutdown = CompletableFuture.runAsync(() -> {shutdownStarted.countDown();service.shutdown(true);});
        try {
            require(shutdownStarted.await(5, TimeUnit.SECONDS), "shutdown never started");
            try {shutdown.get(200, TimeUnit.MILLISECONDS);} catch (TimeoutException expected) {}
        } finally {
            finishAcquire.countDown();
        }
        completion.get(5, TimeUnit.SECONDS);
        shutdown.get(5, TimeUnit.SECONDS);
        require(column.keeps == 0 && column.acquired == column.released, "owner-thread completion raced shutdown and leaked a native hold");
        System.out.println("ChunkLoaderCallbackShutdownRegression PASS");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
