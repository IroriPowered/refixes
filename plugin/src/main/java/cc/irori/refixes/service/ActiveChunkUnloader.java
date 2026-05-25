package cc.irori.refixes.service;

import cc.irori.refixes.config.impl.ChunkUnloaderConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box2D;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// Actively unloads chunks that are outside all players' view range
public class ActiveChunkUnloader {

    private static final HytaleLogger LOGGER = Logs.logger();

    private final Map<String, Long2LongOpenHashMap> outOfRangeSinceByWorld = new ConcurrentHashMap<>();
    private ScheduledFuture<?> task;

    public void registerService() {
        int interval = Math.max(1000, ChunkUnloaderConfig.get().getValue(ChunkUnloaderConfig.CHECK_INTERVAL_MS));
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        execute();
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error while unloading chunks");
                    }
                },
                5000,
                interval,
                TimeUnit.MILLISECONDS);
    }

    public void unregisterService() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        outOfRangeSinceByWorld.clear();
    }

    private void execute() {
        ChunkUnloaderConfig config = ChunkUnloaderConfig.get();

        Map<String, World> worldsByName = Universe.get().getWorlds();

        // Clean cached state for worlds that no longer exist
        outOfRangeSinceByWorld.keySet().removeIf(name -> !worldsByName.containsKey(name));

        for (World world : worldsByName.values()) {
            world.execute(() -> unloadWorld(world, config));
        }
    }

    private void unloadWorld(World world, ChunkUnloaderConfig config) {
        if (!world.getWorldConfig().canUnloadChunks()) {
            return;
        }

        ChunkStore chunkStore = world.getChunkStore();
        int loadedCount = chunkStore.getLoadedChunksCount();
        int minLoaded = Math.max(config.getValue(ChunkUnloaderConfig.MIN_LOADED_CHUNKS), 0);
        if (loadedCount < minLoaded) {
            return;
        }

        int playerCount = world.getPlayerRefs().size();

        // Compatibility with view radius reducer plugins, prevents unloading when no chunks are loaded.
        if (playerCount > 0) {
            boolean anyPlayerChunkLoaded = false;
            for (PlayerRef playerRef : world.getPlayerRefs()) {
                if (playerRef != null && playerRef.getChunkTracker().shouldBeVisible(ChunkUtil.indexChunk(0, 0))) {
                    anyPlayerChunkLoaded = true;
                    break;
                }
            }
            if (!anyPlayerChunkLoaded) {
                return;
            }
        }

        Long2LongOpenHashMap outOfRangeSince = outOfRangeSinceByWorld.computeIfAbsent(world.getName(), key -> {
            Long2LongOpenHashMap map = new Long2LongOpenHashMap();
            map.defaultReturnValue(0L);
            return map;
        });

        long now = System.nanoTime();
        int unloaded = 0;
        int skippedKeepLoaded = 0;
        int skippedKeepLoadedRegion = 0;
        int skippedInRange = 0;
        int skippedWaitingDelay = 0;
        int skippedTickingStripped = 0;
        int skippedEventCancelled = 0;
        int maxUnloads = Math.max(config.getValue(ChunkUnloaderConfig.MAX_UNLOADS_PER_RUN), 1);
        LongSet chunkIndexes = chunkStore.getChunkIndexes();
        LongIterator iterator = chunkIndexes.iterator();
        int totalChunks = chunkIndexes.size();

        while (iterator.hasNext()) {
            long chunkIndex = iterator.nextLong();
            Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
            if (chunkRef == null || !chunkRef.isValid()) {
                outOfRangeSince.remove(chunkIndex);
                continue;
            }

            WorldChunk worldChunk = chunkStore.getStore().getComponent(chunkRef, WorldChunk.getComponentType());
            if (worldChunk == null) {
                outOfRangeSince.remove(chunkIndex);
                continue;
            }

            if (worldChunk.shouldKeepLoaded()) {
                outOfRangeSince.remove(chunkIndex);
                skippedKeepLoaded++;
                continue;
            }
            if (isInKeepLoadedRegion(world, worldChunk)) {
                outOfRangeSince.remove(chunkIndex);
                skippedKeepLoadedRegion++;
                continue;
            }

            // Skip spawn chunk if configured
            if (config.getValue(ChunkUnloaderConfig.KEEP_SPAWN_LOADED) && isSpawnChunk(world, worldChunk)) {
                outOfRangeSince.remove(chunkIndex);
                skippedKeepLoaded++;
                continue;
            }

            // Skip chunks still within a player's view range
            if (isChunkNeeded(world, chunkIndex)) {
                outOfRangeSince.remove(chunkIndex);
                skippedInRange++;
                continue;
            }

            // Start tracking out-of-range time
            long firstOut = outOfRangeSince.get(chunkIndex);
            if (firstOut == 0L) {
                outOfRangeSince.put(chunkIndex, now);
                continue;
            }

            long delayNanos = Math.max(config.getValue(ChunkUnloaderConfig.UNLOAD_DELAY_SECONDS), 1) * 1_000_000_000L;
            if (now - firstOut < delayNanos) {
                skippedWaitingDelay++;
                continue;
            }

            if (worldChunk.is(ChunkFlag.TICKING)) {
                worldChunk.setFlag(ChunkFlag.TICKING, false);
                outOfRangeSince.put(chunkIndex, now);
                skippedTickingStripped++;
                continue;
            }

            ChunkUnloadEvent event = new ChunkUnloadEvent(worldChunk);
            chunkStore.getStore().invoke(chunkRef, event);
            if (event.isCancelled()) {
                if (event.willResetKeepAlive()) {
                    worldChunk.resetKeepAlive();
                }
                outOfRangeSince.remove(chunkIndex);
                skippedEventCancelled++;
                continue;
            }

            chunkStore.remove(chunkRef, RemoveReason.UNLOAD);
            outOfRangeSince.remove(chunkIndex);
            unloaded++;

            if (unloaded >= maxUnloads) {
                break;
            }
        }

        if (unloaded > 0) {
            LOGGER.atInfo().log(
                    "[%s] ChunkUnloader: total=%d, unloaded=%d, inRange=%d, keepLoaded=%d, keepLoadedRegion=%d, waitingDelay=%d, tickingStripped=%d, eventCancelled=%d, players=%d",
                    world.getName(),
                    totalChunks,
                    unloaded,
                    skippedInRange,
                    skippedKeepLoaded,
                    skippedKeepLoadedRegion,
                    skippedWaitingDelay,
                    skippedTickingStripped,
                    skippedEventCancelled,
                    playerCount);
        }
    }

    private boolean isChunkNeeded(World world, long chunkIndex) {
        for (PlayerRef playerRef : world.getPlayerRefs()) {
            if (playerRef != null && playerRef.getChunkTracker().shouldBeVisible(chunkIndex)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInKeepLoadedRegion(World world, WorldChunk worldChunk) {
        Box2D keepLoaded = world.getWorldConfig().getChunkConfig().getKeepLoadedRegion();
        if (keepLoaded == null) {
            return false;
        }
        int minX = ChunkUtil.minBlock(worldChunk.getX());
        int minZ = ChunkUtil.minBlock(worldChunk.getZ());
        int maxX = ChunkUtil.maxBlock(worldChunk.getX());
        int maxZ = ChunkUtil.maxBlock(worldChunk.getZ());
        return maxX >= keepLoaded.min.x
                && minX <= keepLoaded.max.x
                && maxZ >= keepLoaded.min.y
                && minZ <= keepLoaded.max.y;
    }

    private static boolean isSpawnChunk(World world, WorldChunk worldChunk) {
        // Protect spawn chunk at origin (0,0)
        return worldChunk.getX() == 0 && worldChunk.getZ() == 0;
    }
}
