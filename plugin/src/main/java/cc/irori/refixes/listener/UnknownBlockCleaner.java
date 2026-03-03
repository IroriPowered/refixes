package cc.irori.refixes.listener;

import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class UnknownBlockCleaner {

    private static final HytaleLogger LOGGER = Logs.logger();

    private static volatile Field blockChunkField;
    private static volatile boolean fieldSearchDone;

    private UnknownBlockCleaner() {}

    public static void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry()
                .registerGlobal(ChunkPreLoadProcessEvent.class, UnknownBlockCleaner::cleanUnknownBlocks);
    }

    private static void cleanUnknownBlocks(ChunkPreLoadProcessEvent event) {
        WorldChunk chunk = event.getChunk();
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
        for (int x = 0; x < ChunkUtil.SIZE; x++) {
            for (int z = 0; z < ChunkUtil.SIZE; z++) {
                for (int y = 0; y < ChunkUtil.HEIGHT; y++) {
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
