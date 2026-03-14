package cc.irori.refixes.service;

import cc.irori.refixes.util.Logs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkLoaderService {

    private static final HytaleLogger LOGGER = Logs.logger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataDir;

    private final Map<String, Map<Long, String>> keptChunksByWorld = new ConcurrentHashMap<>();

    public ChunkLoaderService(Path pluginDataDir) {
        this.dataDir = pluginDataDir.resolve("chunkloaders");
        loadAll();
    }

    public void addChunk(World world, int chunkX, int chunkZ, String label) {
        long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
        String worldName = world.getName();

        keptChunksByWorld
                .computeIfAbsent(worldName, k -> new ConcurrentHashMap<>())
                .put(chunkIndex, label != null ? label : "");
        save(worldName);

        world.execute(() -> {
            Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
            if (chunkRef != null && chunkRef.isValid()) {
                WorldChunk chunk =
                        world.getChunkStore().getStore().getComponent(chunkRef, WorldChunk.getComponentType());
                if (chunk != null) {
                    chunk.addKeepLoaded();
                    LOGGER.atInfo().log("Added chunk loader at %d, %d in world %s", chunkX, chunkZ, worldName);
                }
            }
        });
    }

    public void removeChunk(World world, int chunkX, int chunkZ) {
        long chunkIndex = ChunkUtil.indexChunk(chunkX, chunkZ);
        String worldName = world.getName();

        Map<Long, String> chunks = keptChunksByWorld.get(worldName);
        if (chunks != null) {
            chunks.remove(chunkIndex);
            save(worldName);
        }

        world.execute(() -> {
            Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
            if (chunkRef != null && chunkRef.isValid()) {
                WorldChunk chunk =
                        world.getChunkStore().getStore().getComponent(chunkRef, WorldChunk.getComponentType());
                if (chunk != null) {
                    chunk.removeKeepLoaded();
                    LOGGER.atInfo().log("Removed chunk loader at %d, %d in world %s", chunkX, chunkZ, worldName);
                }
            }
        });
    }

    public Map<Long, String> getKeptChunks(String worldName) {
        return keptChunksByWorld.getOrDefault(worldName, new ConcurrentHashMap<>());
    }

    public Long findChunkByLabel(String worldName, String label) {
        Map<Long, String> chunks = keptChunksByWorld.get(worldName);
        if (chunks == null) {
            return null;
        }
        for (Map.Entry<Long, String> entry : chunks.entrySet()) {
            if (label.equalsIgnoreCase(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void loadWorld(World world) {
        String worldName = world.getName();
        Map<Long, String> chunks = keptChunksByWorld.get(worldName);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        world.execute(() -> {
            int loaded = 0;
            for (long chunkIndex : chunks.keySet()) {
                Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
                if (chunkRef != null && chunkRef.isValid()) {
                    WorldChunk chunk =
                            world.getChunkStore().getStore().getComponent(chunkRef, WorldChunk.getComponentType());
                    if (chunk != null) {
                        chunk.addKeepLoaded();
                        loaded++;
                    }
                }
            }
            if (loaded > 0) {
                LOGGER.atInfo().log("Loaded %d chunk loaders in world %s", loaded, worldName);
            }
        });
    }

    public void unloadWorld(World world) {
        String worldName = world.getName();
        Map<Long, String> chunks = keptChunksByWorld.get(worldName);
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        world.execute(() -> {
            for (long chunkIndex : chunks.keySet()) {
                Ref<ChunkStore> chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
                if (chunkRef != null && chunkRef.isValid()) {
                    WorldChunk chunk =
                            world.getChunkStore().getStore().getComponent(chunkRef, WorldChunk.getComponentType());
                    if (chunk != null) {
                        chunk.removeKeepLoaded();
                    }
                }
            }
        });
    }

    private void save(String worldName) {
        try {
            Files.createDirectories(dataDir);
            Map<Long, String> chunks = keptChunksByWorld.get(worldName);
            if (chunks == null || chunks.isEmpty()) {
                Files.deleteIfExists(dataDir.resolve(worldName + ".json"));
                return;
            }

            List<ChunkData> chunkDataList = new ArrayList<>();
            for (Map.Entry<Long, String> entry : chunks.entrySet()) {
                long chunkIndex = entry.getKey();
                chunkDataList.add(new ChunkData(
                        ChunkUtil.xOfChunkIndex(chunkIndex), ChunkUtil.zOfChunkIndex(chunkIndex), entry.getValue()));
            }

            String json = GSON.toJson(chunkDataList);
            Files.writeString(dataDir.resolve(worldName + ".json"), json);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save chunk loaders for world %s", worldName);
        }
    }

    private void loadAll() {
        try {
            if (!Files.exists(dataDir)) {
                return;
            }

            Files.list(dataDir)
                    .filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String worldName = path.getFileName().toString().replace(".json", "");
                            String json = Files.readString(path);
                            List<ChunkData> chunkDataList =
                                    GSON.fromJson(json, new TypeToken<List<ChunkData>>() {}.getType());

                            Map<Long, String> chunks = new ConcurrentHashMap<>();
                            for (ChunkData data : chunkDataList) {
                                chunks.put(ChunkUtil.indexChunk(data.x, data.z), data.label != null ? data.label : "");
                            }
                            keptChunksByWorld.put(worldName, chunks);
                            LOGGER.atInfo().log("Loaded %d chunk loaders for world %s", chunks.size(), worldName);
                        } catch (Exception e) {
                            LOGGER.atSevere().withCause(e).log("Failed to load chunk loaders from %s", path);
                        }
                    });
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load chunk loaders");
        }
    }

    private static class ChunkData {
        int x, z;
        String label;

        ChunkData(int x, int z, String label) {
            this.x = x;
            this.z = z;
            this.label = label;
        }
    }
}
