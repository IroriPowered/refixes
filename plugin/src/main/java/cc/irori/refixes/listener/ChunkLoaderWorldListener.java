package cc.irori.refixes.listener;

import cc.irori.refixes.service.ChunkLoaderService;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;

public class ChunkLoaderWorldListener {

    private final ChunkLoaderService chunkLoaderService;

    public ChunkLoaderWorldListener(ChunkLoaderService chunkLoaderService) {
        this.chunkLoaderService = chunkLoaderService;
    }

    public void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry().registerGlobal(StartWorldEvent.class, this::onWorldStart);
    }

    private void onWorldStart(StartWorldEvent event) {
        chunkLoaderService.loadWorld(event.getWorld());
    }
}
