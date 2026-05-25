package cc.irori.refixes.listener;

import cc.irori.refixes.config.impl.ListenerConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public final class UnknownBlockCleaner {

    private static final HytaleLogger LOGGER = Logs.logger();
    private static final Queue<WorldChunk> pendingChunks = new ConcurrentLinkedQueue<>();

    private static volatile Field blockChunkField;
    private static volatile boolean fieldSearchDone;

    private UnknownBlockCleaner() {}

    public static void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry()
                .registerGlobal(ChunkPreLoadProcessEvent.class, event -> pendingChunks.add(event.getChunk()));

        int intervalMs = Math.max(20, ListenerConfig.get().getValue(ListenerConfig.UNKNOWN_BLOCK_CLEANER_INTERVAL_MS));
        HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        drainQueue();
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error in unknown block cleaner");
                    }
                },
                1000,
                intervalMs,
                TimeUnit.MILLISECONDS);
    }

    private static void drainQueue() {
        if (pendingChunks.isEmpty()) {
            return;
        }

        Map<World, List<WorldChunk>> byWorld = new HashMap<>();
        WorldChunk chunk;
        while ((chunk = pendingChunks.poll()) != null) {
            World world = chunk.getWorld();
            if (world != null) {
                byWorld.computeIfAbsent(world, k -> new ArrayList<>()).add(chunk);
            }
        }

        for (Map.Entry<World, List<WorldChunk>> entry : byWorld.entrySet()) {
            List<WorldChunk> chunks = entry.getValue();
            entry.getKey().execute(() -> processChunks(chunks));
        }
    }

    private static void processChunks(List<WorldChunk> chunks) {
        int budgetMs = Math.max(1, ListenerConfig.get().getValue(ListenerConfig.UNKNOWN_BLOCK_CLEANER_BUDGET_MS));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs);
        for (int i = 0; i < chunks.size(); i++) {
            cleanChunk(chunks.get(i));
            if (System.nanoTime() > deadline && i + 1 < chunks.size()) {
                for (int j = i + 1; j < chunks.size(); j++) {
                    pendingChunks.add(chunks.get(j));
                }
                return;
            }
        }
    }

    private static void cleanChunk(WorldChunk chunk) {
        Map<String, Integer> removedCounts = new HashMap<>();

        BlockChunk blockChunk = resolveBlockChunk(chunk);
        if (blockChunk != null) {
            for (int sectionY = 0; sectionY < ChunkUtil.HEIGHT; sectionY += ChunkUtil.SIZE) {
                BlockSection section = blockChunk.getSectionAtBlockY(sectionY);
                if (section == null || section.isSolidAir()) {
                    continue;
                }
                cleanSection(chunk, sectionY, removedCounts);
            }
        } else {
            cleanAll(chunk, removedCounts);
        }

        if (!removedCounts.isEmpty()) {
            int total =
                    removedCounts.values().stream().mapToInt(Integer::intValue).sum();
            LOGGER.atInfo().log(
                    "Cleaned %d unknown blocks (%d types) from chunk (%d, %d) in world '%s': %s",
                    total,
                    removedCounts.size(),
                    chunk.getX(),
                    chunk.getZ(),
                    chunk.getWorld().getName(),
                    removedCounts);
        }
    }

    private static void cleanSection(WorldChunk chunk, int sectionStartY, Map<String, Integer> removedCounts) {
        int sectionEndY = sectionStartY + ChunkUtil.SIZE;
        for (int x = 0; x < ChunkUtil.SIZE; x++) {
            for (int z = 0; z < ChunkUtil.SIZE; z++) {
                for (int y = sectionStartY; y < sectionEndY; y++) {
                    try {
                        BlockType blockType = chunk.getBlockType(x, y, z);
                        if (blockType != null && blockType.isUnknown()) {
                            removedCounts.merge(blockType.getId(), 1, Integer::sum);
                            chunk.setBlock(x, y, z, BlockType.EMPTY_KEY);
                        }
                    } catch (Throwable t) {
                        int blockX = chunk.getX() * ChunkUtil.SIZE + x;
                        int blockZ = chunk.getZ() * ChunkUtil.SIZE + z;
                        LOGGER.atWarning().withCause(t).log(
                                "Error cleaning block at (%d, %d, %d) in world '%s'",
                                blockX, y, blockZ, chunk.getWorld().getName());
                    }
                }
            }
        }
    }

    private static void cleanAll(WorldChunk chunk, Map<String, Integer> removedCounts) {
        for (int sectionY = 0; sectionY < ChunkUtil.HEIGHT; sectionY += ChunkUtil.SIZE) {
            cleanSection(chunk, sectionY, removedCounts);
        }
    }

    private static BlockChunk resolveBlockChunk(Object worldChunk) {
        if (!fieldSearchDone) {
            synchronized (UnknownBlockCleaner.class) {
                if (!fieldSearchDone) {
                    try {
                        for (Field field : WorldChunk.class.getDeclaredFields()) {
                            if (BlockChunk.class.isAssignableFrom(field.getType())) {
                                field.setAccessible(true);
                                blockChunkField = field;
                                break;
                            }
                        }
                    } catch (Throwable t) {
                        LOGGER.atWarning().withCause(t).log(
                                "Failed to locate BlockChunk field on WorldChunk via reflection");
                    }
                    fieldSearchDone = true;
                }
            }
        }

        if (blockChunkField == null) {
            return null;
        }
        try {
            return (BlockChunk) blockChunkField.get(worldChunk);
        } catch (Exception e) {
            return null;
        }
    }
}
