package cc.irori.refixes.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box2D;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ecs.ChunkUnloadEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import cc.irori.refixes.config.impl.ChunkUnloaderConfig;
import cc.irori.refixes.util.Logs;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;

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

        int offset = Math.max(config.getValue(ChunkUnloaderConfig.UNLOAD_DISTANCE_OFFSET), 0);

        for (World world : worldsByName.values()) {
            world.execute(() -> unloadWorld(world, offset, config));
        }
    }

    private void unloadWorld(World world, int offset, ChunkUnloaderConfig config) {
        if (!world.getWorldConfig().canUnloadChunks()) {
            return;
        }

        ChunkStore chunkStore = world.getChunkStore();
        int loadedCount = chunkStore.getLoadedChunksCount();
        int minLoaded = Math.max(config.getValue(ChunkUnloaderConfig.MIN_LOADED_CHUNKS), 0);
        if (loadedCount < minLoaded) {
            return;
        }

        List<PlayerSnapshot> playerSnapshots = collectPlayerSnapshots(world.getPlayerRefs(), offset);

        // Compatibility  with view radius reducer plugins, prevents unloading when no chunks are loaded.
        if (!playerSnapshots.isEmpty()) {
            boolean anyPlayerChunkLoaded = false;
            for (PlayerSnapshot snapshot : playerSnapshots) {
                if (chunkStore.getChunkReference(snapshot.chunkIndex()) != null) {
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

            // Skip chunks still within a player's safe radius (per-player view radius + offset)
            if (isChunkNeeded(playerSnapshots, chunkIndex)) {
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
                    "[%s] ChunkUnloader: total=%d, unloaded=%d, inRange=%d, keepLoaded=%d, keepLoadedRegion=%d, waitingDelay=%d, tickingStripped=%d, eventCancelled=%d, players=%d, offset=%d",
                    world.getName(),
                    totalChunks,
                    unloaded,
                    skippedInRange,
                    skippedKeepLoaded,
                    skippedKeepLoadedRegion,
                    skippedWaitingDelay,
                    skippedTickingStripped,
                    skippedEventCancelled,
                    playerSnapshots.size(),
                    offset);
        }
    }

    private static List<PlayerSnapshot> collectPlayerSnapshots(java.util.Collection<PlayerRef> players, int offset) {
        List<PlayerSnapshot> snapshots = new ArrayList<>();
        if (players == null || players.isEmpty()) {
            return snapshots;
        }
        for (PlayerRef playerRef : players) {
            if (playerRef == null) {
                continue;
            }
            Transform transform = playerRef.getTransform();
            int chunkX = ChunkUtil.chunkCoordinate(transform.getPosition().getX());
            int chunkZ = ChunkUtil.chunkCoordinate(transform.getPosition().getZ());
            long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);

            int viewRadius = Player.DEFAULT_VIEW_RADIUS_CHUNKS;
            try {
                Ref<EntityStore> entityRef = playerRef.getReference();
                if (entityRef != null && entityRef.isValid()) {
                    Player player = entityRef.getStore().getComponent(entityRef, Player.getComponentType());
                    if (player != null) {
                        viewRadius = player.getViewRadius();
                    }
                }
            } catch (Throwable ignored) {
                // Fall back to default
            }

            snapshots.add(new PlayerSnapshot(chunkIndex, Math.max(viewRadius + offset, 2)));
        }
        return snapshots;
    }

    private static boolean isChunkNeeded(List<PlayerSnapshot> playerSnapshots, long chunkIndex) {
        for (PlayerSnapshot snapshot : playerSnapshots) {
            if (chebyshevDistance(chunkIndex, snapshot.chunkIndex) <= snapshot.safeRadius) {
                return true;
            }
        }
        return false;
    }

    private record PlayerSnapshot(long chunkIndex, int safeRadius) {}

    private static int chebyshevDistance(long index1, long index2) {
        int x1 = ChunkUtil.xOfChunkIndex(index1);
        int z1 = ChunkUtil.zOfChunkIndex(index1);
        int x2 = ChunkUtil.xOfChunkIndex(index2);
        int z2 = ChunkUtil.zOfChunkIndex(index2);
        return Math.max(Math.abs(x1 - x2), Math.abs(z1 - z2));
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
}
