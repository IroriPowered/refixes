package cc.irori.refixes.listener;

import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent;

public final class UnknownBlockCleaner {

    private static final HytaleLogger LOGGER = Logs.logger();

    // Private constructor to prevent instantiation
    private UnknownBlockCleaner() {}

    public static void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry()
                .registerGlobal(ChunkPreLoadProcessEvent.class, UnknownBlockCleaner::cleanUnknownBlocks);
    }

    private static void cleanUnknownBlocks(ChunkPreLoadProcessEvent event) {
        WorldChunk chunk = event.getChunk();
        for (int x = 0; x < ChunkUtil.SIZE; x++) {
            for (int z = 0; z < ChunkUtil.SIZE; z++) {
                for (int y = 0; y < ChunkUtil.HEIGHT; y++) {
                    int blockX = chunk.getX() * ChunkUtil.SIZE + x;
                    int blockZ = chunk.getZ() * ChunkUtil.SIZE + z;

                    try {
                        BlockType blockType = chunk.getBlockType(x, y, z);
                        if (blockType != null && blockType.isUnknown()) {
                            LOGGER.atWarning().log(
                                    "Removed unknown block '%s' at position (%d, %d, %d), world '%s'",
                                    blockType.getId(),
                                    blockX,
                                    y,
                                    blockZ,
                                    chunk.getWorld().getName());
                            chunk.setBlock(x, y, z, BlockType.EMPTY_KEY);
                        }
                    } catch (Throwable t) {
                        LOGGER.atWarning().withCause(t).log(
                                "Failed to scan for unknown blocks at position (%d, %d, %d), world '%s'",
                                chunk.getX(),
                                chunk.getZ(),
                                blockX,
                                y,
                                blockZ,
                                chunk.getWorld().getName());
                    }
                }
            }
        }
    }
}
