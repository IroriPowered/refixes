package cc.irori.refixes.service;

import cc.irori.refixes.util.Logs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class ChunkLoaderService {
    private static final HytaleLogger LOGGER = Logs.logger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataDir;
    private final Map<String, Map<Long, ChunkData>> keptChunksByWorld = new ConcurrentHashMap<>();
    private final Map<World, Map<Long, Hold>> holdsByWorld = new ConcurrentHashMap<>();
    private volatile boolean stopped;

    public ChunkLoaderService(Path pluginDataDir) {
        dataDir = pluginDataDir.resolve("chunkloaders");
        loadAll();
    }

    public void addChunk(World world, int chunkX, int chunkZ, String label, int sectionY) {
        long index = ChunkUtil.indexChunk(chunkX, chunkZ);
        Map<Long, ChunkData> chunks =
                keptChunksByWorld.computeIfAbsent(world.getName(), k -> new ConcurrentHashMap<>());
        chunks.compute(index, (key, existing) -> {
            if (existing == null) existing = new ChunkData(chunkX, chunkZ, label);
            existing.label = label != null ? label : "";
            existing.sections.add(sectionY);
            return existing;
        });
        save(world.getName());
        keepChunkLoaded(world, index);
    }

    private void keepChunkLoaded(World world, long index) {
        executeOnWorld(world, () -> {
            ChunkData registration = registration(world, index);
            if (stopped || registration == null) return;
            Map<Long, Hold> holds = holdsByWorld.computeIfAbsent(world, k -> new ConcurrentHashMap<>());
            Hold existing = holds.get(index);
            if (existing != null) {
                if (existing.chunk != null) loadSections(world, index, existing, registration);
                return;
            }
            Hold hold = new Hold();
            holds.put(index, hold);
            world.getChunkStore()
                    .getChunkReferenceAsync(index, GetChunkFlags.SET_TICKING)
                    .whenComplete((ref, error) -> executeOnWorld(world, () -> {
                        if (holdsByWorld.get(world) != holds
                                || holds.get(index) != hold
                                || registration(world, index) == null
                                || stopped) return;
                        if (error != null || ref == null || !ref.isValid()) {
                            holds.remove(index, hold);
                            LOGGER.atWarning().withCause(error).log(
                                    "Failed to load retained column %d in %s", index, world.getName());
                            return;
                        }
                        WorldChunk chunk =
                                world.getChunkStore().getStore().getComponent(ref, WorldChunk.getComponentType());
                        if (chunk == null) {
                            holds.remove(index, hold);
                            return;
                        }
                        hold.chunk = chunk;
                        chunk.addKeepLoaded();
                        loadSections(world, index, hold, registration(world, index));
                    }));
        });
    }

    private void loadSections(World world, long index, Hold hold, ChunkData registration) {
        if (registration == null) return;
        for (int sectionY : registration.sections) {
            if (!hold.requestedSections.add(sectionY)) continue;
            world.getChunkStore()
                    .getChunkSectionReferenceAsync(registration.x, sectionY, registration.z, GetChunkFlags.SET_TICKING)
                    .whenComplete((ref, error) -> executeOnWorld(world, () -> {
                        if (error != null || ref == null || !ref.isValid()) {
                            hold.requestedSections.remove(sectionY);
                            LOGGER.atWarning().withCause(error).log(
                                    "Failed to load retained section %d, %d, %d in %s",
                                    registration.x, sectionY, registration.z, world.getName());
                            return;
                        }
                        Map<Long, Hold> holds = holdsByWorld.get(world);
                        if (stopped || holds == null || holds.get(index) != hold) return;
                        ChunkSection section =
                                world.getChunkStore().getStore().getComponent(ref, ChunkSection.getComponentType());
                        if (section != null) {
                            section.resetKeepAlive();
                            section.resetActiveTimer();
                        }
                    }));
        }
    }

    private static void executeOnWorld(World world, Runnable task) {
        try {
            world.execute(() -> {
                if (!world.getChunkStore().getStore().isShutdown()) task.run();
            });
        } catch (SkipSentryException ignored) {
        }
    }

    public boolean removeChunk(World world, int chunkX, int chunkZ) {
        return removeChunks(world, List.of(ChunkUtil.indexChunk(chunkX, chunkZ))) != 0;
    }

    public int addChunks(World world, Collection<Long> indexes, int minSectionY, int maxSectionY) {
        Map<Long, ChunkData> chunks =
                keptChunksByWorld.computeIfAbsent(world.getName(), k -> new ConcurrentHashMap<>());
        int added = 0;
        for (long index : indexes) {
            ChunkData data = new ChunkData(ChunkUtil.xOfChunkIndex(index), ChunkUtil.zOfChunkIndex(index), "");
            ChunkData previous = chunks.putIfAbsent(index, data);
            if (previous == null) added++;
            else data = previous;
            for (int y = minSectionY; ; y++) {
                data.sections.add(y);
                if (y == maxSectionY) break;
            }
        }
        save(world.getName());
        for (long index : indexes) keepChunkLoaded(world, index);
        return added;
    }

    public int removeChunks(World world, Collection<Long> indexes) {
        Map<Long, ChunkData> chunks = keptChunksByWorld.get(world.getName());
        if (chunks == null) return 0;
        List<Long> removed = new ArrayList<>();
        for (long index : indexes) {
            if (chunks.remove(index) != null) removed.add(index);
        }
        if (removed.isEmpty()) return 0;
        save(world.getName());
        executeOnWorld(world, () -> {
            Map<Long, Hold> holds = holdsByWorld.get(world);
            if (holds == null) return;
            for (long index : removed) {
                Hold hold = holds.remove(index);
                if (hold != null && hold.chunk != null) hold.chunk.removeKeepLoaded();
                if (registration(world, index) != null) keepChunkLoaded(world, index);
            }
        });
        return removed.size();
    }

    public Map<Long, String> getKeptChunks(String worldName) {
        Map<Long, ChunkData> chunks = keptChunksByWorld.get(worldName);
        if (chunks == null) return Map.of();
        Map<Long, String> labels = new java.util.HashMap<>();
        chunks.forEach((index, data) -> labels.put(index, data.label));
        return Map.copyOf(labels);
    }

    public Long findChunkByLabel(String worldName, String label) {
        Map<Long, ChunkData> chunks = keptChunksByWorld.get(worldName);
        if (chunks != null) {
            for (var entry : chunks.entrySet()) {
                if (label.equalsIgnoreCase(entry.getValue().label)) return entry.getKey();
            }
        }
        return null;
    }

    public void loadWorld(World world) {
        Map<Long, ChunkData> chunks = keptChunksByWorld.get(world.getName());
        if (chunks != null) for (long index : chunks.keySet()) keepChunkLoaded(world, index);
    }

    public void unloadWorld(World world) {
        Map<Long, Hold> holds = holdsByWorld.remove(world);
        if (holds == null) return;
        executeOnWorld(world, () -> {
            for (Hold hold : holds.values()) {
                if (hold.chunk != null) hold.chunk.removeKeepLoaded();
            }
        });
    }

    public void shutdown() {
        stopped = true;
        for (World world : List.copyOf(holdsByWorld.keySet())) unloadWorld(world);
    }

    public void retainSections(Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        Map<Long, ChunkData> registrations = keptChunksByWorld.get(world.getName());
        if (stopped || registrations == null || registrations.isEmpty()) return;
        boolean[] changed = {false};
        store.forEachChunk(ChunkSection.getComponentType(), (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                ChunkSection section = chunk.getComponent(i, ChunkSection.getComponentType());
                ChunkData data = registrations.get(ChunkUtil.indexChunk(section.getX(), section.getZ()));
                if (data == null) continue;
                section.resetKeepAlive();
                section.resetActiveTimer();
                commandBuffer.tryRemoveComponent(
                        chunk.getReferenceTo(i), ChunkStore.REGISTRY.getNonTickingComponentType());
                if (data.sections.add(section.getY())) changed[0] = true;
            }
        });
        if (changed[0]) save(world.getName());
    }

    private ChunkData registration(World world, long index) {
        Map<Long, ChunkData> chunks = keptChunksByWorld.get(world.getName());
        return chunks == null ? null : chunks.get(index);
    }

    private synchronized void save(String worldName) {
        try {
            Files.createDirectories(dataDir);
            Map<Long, ChunkData> chunks = keptChunksByWorld.get(worldName);
            if (chunks == null || chunks.isEmpty()) Files.deleteIfExists(dataDir.resolve(worldName + ".json"));
            else Files.writeString(dataDir.resolve(worldName + ".json"), GSON.toJson(new ArrayList<>(chunks.values())));
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to save chunk loaders for world %s", worldName);
        }
    }

    private void loadAll() {
        if (!Files.exists(dataDir)) return;
        try (Stream<Path> paths = Files.list(dataDir)) {
            paths.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                try {
                    String worldName = path.getFileName().toString().replace(".json", "");
                    List<ChunkData> data =
                            GSON.fromJson(Files.readString(path), new TypeToken<List<ChunkData>>() {}.getType());
                    Map<Long, ChunkData> chunks = new ConcurrentHashMap<>();
                    for (ChunkData entry : data) {
                        entry.label = entry.label != null ? entry.label : "";
                        Set<Integer> sections = ConcurrentHashMap.newKeySet();
                        if (entry.sections != null) sections.addAll(entry.sections);
                        entry.sections = sections;
                        chunks.put(ChunkUtil.indexChunk(entry.x, entry.z), entry);
                    }
                    keptChunksByWorld.put(worldName, chunks);
                } catch (Exception e) {
                    LOGGER.atSevere().withCause(e).log("Failed to load chunk loaders from %s", path);
                }
            });
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load chunk loaders");
        }
    }

    private static final class Hold {
        private WorldChunk chunk;
        private final Set<Integer> requestedSections = ConcurrentHashMap.newKeySet();
    }

    private static final class ChunkData {
        private int x, z;
        private String label;
        private Set<Integer> sections = ConcurrentHashMap.newKeySet();

        private ChunkData(int x, int z, String label) {
            this.x = x;
            this.z = z;
            this.label = label != null ? label : "";
        }
    }
}
