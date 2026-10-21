package cc.irori.refixes.service;

import cc.irori.refixes.util.Logs;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Ref;
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
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ChunkLoaderService {
    private static final HytaleLogger LOGGER = Logs.logger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataDir;
    private final Map<String, Map<Long, ChunkData>> keptChunksByWorld = new HashMap<>();
    private final Map<World, WorldState> worlds = new IdentityHashMap<>();
    private final Map<String, World> currentWorlds = new HashMap<>();
    private final CoalescingPersistence<List<Snapshot>> persistence;
    private boolean stopped;

    public ChunkLoaderService(Path pluginDataDir) {
        dataDir = pluginDataDir.resolve("chunkloaders");
        loadAll();
        persistence = new CoalescingPersistence<>((worldName, snapshot) -> {
            try {
                writeSnapshot(worldName, snapshot);
            } catch (IOException e) {
                LOGGER.atSevere().withCause(e).log("Failed to save chunk loaders for world %s", worldName);
                throw e;
            }
        });
    }

    public synchronized void addChunk(World world, int chunkX, int chunkZ, String label, int sectionY) {
        if (stopped) return;
        long index = ChunkUtil.indexChunk(chunkX, chunkZ);
        Map<Long, ChunkData> chunks = keptChunksByWorld.computeIfAbsent(world.getName(), k -> new HashMap<>());
        ChunkData data = chunks.computeIfAbsent(index, k -> new ChunkData(chunkX, chunkZ, label));
        data.label = label != null ? label : "";
        data.sections.add(sectionY);
        save(world.getName());
        refreshWorld(world);
    }

    public synchronized int addChunks(World world, Collection<Long> indexes, int minSectionY, int maxSectionY) {
        if (stopped) return 0;
        if (minSectionY > maxSectionY) throw new IllegalArgumentException("Invalid section range");
        Map<Long, ChunkData> chunks = keptChunksByWorld.computeIfAbsent(world.getName(), k -> new HashMap<>());
        int added = 0;
        for (long index : indexes) {
            ChunkData data = chunks.get(index);
            if (data == null) {
                data = new ChunkData(ChunkUtil.xOfChunkIndex(index), ChunkUtil.zOfChunkIndex(index), "");
                chunks.put(index, data);
                added++;
            }
            for (int y = minSectionY; ; y++) {
                data.sections.add(y);
                if (y == maxSectionY) break;
            }
        }
        save(world.getName());
        refreshWorld(world);
        return added;
    }

    public boolean removeChunk(World world, int chunkX, int chunkZ) {
        return removeChunks(world, List.of(ChunkUtil.indexChunk(chunkX, chunkZ))) != 0;
    }

    public synchronized int removeChunks(World world, Collection<Long> indexes) {
        if (stopped) return 0;
        Map<Long, ChunkData> chunks = keptChunksByWorld.get(world.getName());
        if (chunks == null) return 0;
        Map<Long, ChunkData> removed = new HashMap<>();
        for (long index : indexes) {
            ChunkData data = chunks.remove(index);
            if (data != null) removed.put(index, data);
        }
        if (removed.isEmpty()) return 0;
        save(world.getName());
        executeOnWorld(world, () -> {
            WorldState state = worlds.get(world);
            if (state == null) return;
            removed.forEach((index, registration) -> {
                Hold hold = state.holds.get(index);
                if (hold != null && hold.registration == registration) {
                    state.holds.remove(index);
                    hold.clear();
                }
            });
        });
        return removed.size();
    }

    public synchronized Map<Long, String> getKeptChunks(String worldName) {
        Map<Long, ChunkData> chunks = keptChunksByWorld.get(worldName);
        if (chunks == null) return Map.of();
        Map<Long, String> labels = new HashMap<>();
        chunks.forEach((index, data) -> labels.put(index, data.label));
        return Map.copyOf(labels);
    }

    public synchronized Long findChunkByLabel(String worldName, String label) {
        Map<Long, ChunkData> chunks = keptChunksByWorld.get(worldName);
        if (chunks != null) {
            for (var entry : chunks.entrySet()) {
                if (label.equalsIgnoreCase(entry.getValue().label)) return entry.getKey();
            }
        }
        return null;
    }

    public synchronized void loadWorld(World world) {
        if (stopped) return;
        World previous = currentWorlds.put(world.getName(), world);
        if (previous != null && previous != world) unloadWorld(previous);
        WorldState state = worlds.computeIfAbsent(world, ignored -> new WorldState());
        state.reconcile = true;
        executeOnWorld(world, () -> maintain(world, state, world.getChunkStore().getStore()));
    }

    private void refreshWorld(World world) {
        if (!worlds.containsKey(world)) {
            if (currentWorlds.containsKey(world.getName())) return;
            loadWorld(world);
            return;
        }
        WorldState state = worlds.get(world);
        state.reconcile = true;
        executeOnWorld(world, () -> maintain(world, state, world.getChunkStore().getStore()));
    }

    public synchronized void unloadWorld(World world) {
        WorldState state = worlds.remove(world);
        currentWorlds.remove(world.getName(), world);
        if (state == null) return;
        if (state.dirty && !stopped) save(world.getName());
        state.clear();
    }

    public void shutdown(boolean serverStopping) {
        synchronized (this) {
            if (stopped) return;
            for (var entry : worlds.entrySet()) {
                if (entry.getValue().dirty) save(entry.getKey().getName());
            }
            stopped = true;
            for (World world : List.copyOf(worlds.keySet())) unloadWorld(world);
        }
        try {
            persistence.close(serverStopping ? TimeUnit.SECONDS.toNanos(30) : Long.MAX_VALUE);
        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to flush chunk loader registrations");
        }
    }

    private void executeOnWorld(World world, Runnable task) {
        try {
            Store<ChunkStore> store = world.getChunkStore().getStore();
            if (store != null && store.isInThread()) {
                synchronized (ChunkLoaderService.this) {
                    task.run();
                }
                return;
            }
            world.execute(() -> {
                synchronized (ChunkLoaderService.this) {
                    task.run();
                }
            });
        } catch (SkipSentryException ignored) {
        }
    }

    private Hold hold(World world, WorldState state, long index) {
        ChunkData registration = registration(world, index);
        if (stopped || worlds.get(world) != state || registration == null) {
            return null;
        }
        Hold hold = state.holds.get(index);
        if (hold != null && hold.registration != registration) {
            state.holds.remove(index);
            hold.clear();
            hold = null;
        }
        if (hold == null) {
            hold = new Hold(registration);
            state.holds.put(index, hold);
        }
        return hold;
    }

    private boolean current(World world, WorldState state, long index, Hold hold) {
        return !stopped
                && worlds.get(world) == state
                && state.holds.get(index) == hold
                && registration(world, index) == hold.registration;
    }

    private void load(World world, WorldState state, long index, Hold hold) {
        if (System.nanoTime() < hold.retryAfter) return;
        if (hold.column.get() == null && !hold.loadingColumn) {
            hold.loadingColumn = true;
            try {
                var future = world.getChunkStore().getChunkReferenceAsync(index, GetChunkFlags.SET_TICKING);
                future.whenComplete((ref, error) -> executeOnWorld(world, () -> {
                    if (!current(world, state, index, hold)) return;
                    hold.loadingColumn = false;
                    if (error != null || ref == null || !ref.isValid()) {
                        failed(world, hold, error);
                        return;
                    }
                    WorldChunk column =
                            world.getChunkStore().getStore().getComponent(ref, WorldChunk.getComponentType());
                    if (column == null) {
                        failed(world, hold, null);
                        return;
                    }
                    hold.columnRef = ref;
                    hold.column.set(column);
                    load(world, state, index, hold);
                }));
            } catch (RuntimeException e) {
                hold.loadingColumn = false;
                failed(world, hold, e);
            }
        }
        if (hold.column.get() == null) return;
        for (int y : List.copyOf(hold.registration.sections)) {
            if (hold.sections.containsKey(y) || !hold.requested.add(y)) continue;
            try {
                var future = world.getChunkStore()
                        .getChunkSectionReferenceAsync(
                                hold.registration.x, y, hold.registration.z, GetChunkFlags.SET_TICKING);
                future.whenComplete((ref, error) -> executeOnWorld(world, () -> {
                    if (!current(world, state, index, hold)) return;
                    hold.requested.remove(y);
                    if (error != null || ref == null || !ref.isValid()) {
                        failed(world, hold, error);
                        return;
                    }
                    ChunkSection section =
                            world.getChunkStore().getStore().getComponent(ref, ChunkSection.getComponentType());
                    if (section == null) failed(world, hold, null);
                    else sectionAdded(world.getChunkStore().getStore(), ref, section);
                }));
            } catch (RuntimeException e) {
                hold.requested.remove(y);
                failed(world, hold, e);
            }
        }
    }

    private static void failed(World world, Hold hold, Throwable error) {
        hold.retryAfter = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        LOGGER.atWarning().withCause(error).log(
                "Failed to load retained column/section %d, %d in %s",
                hold.registration.x, hold.registration.z, world.getName());
    }

    public synchronized void sectionAdded(Store<ChunkStore> store, Ref<ChunkStore> ref, ChunkSection section) {
        World world = store.getExternalData().getWorld();
        WorldState state = worlds.get(world);
        if (state == null || section == null) return;
        long index = ChunkUtil.indexChunk(section.getX(), section.getZ());
        Hold hold = hold(world, state, index);
        if (hold == null) return;
        SectionHold owned = hold.sections.get(section.getY());
        if (owned == null) {
            owned = new SectionHold();
            hold.sections.put(section.getY(), owned);
        }
        owned.reference = ref;
        owned.section.set(section);
        section.resetActiveTimer();
        if (hold.registration.sections.add(section.getY())) state.dirty = true;
    }

    public synchronized void sectionRemoved(Store<ChunkStore> store, Ref<ChunkStore> ref, ChunkSection section) {
        WorldState state = worlds.get(store.getExternalData().getWorld());
        if (state == null || section == null) return;
        Hold hold = state.holds.get(ChunkUtil.indexChunk(section.getX(), section.getZ()));
        if (hold == null) return;
        SectionHold owned = hold.sections.get(section.getY());
        if (owned != null && owned.reference == ref && owned.section.get() == section) {
            hold.sections.remove(section.getY());
            owned.section.clear();
        }
    }

    public synchronized void retainSections(Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        WorldState state = worlds.get(world);
        if (state != null) maintain(world, state, store);
    }

    private void maintain(World world, WorldState state, Store<ChunkStore> store) {
        if (stopped || worlds.get(world) != state || store.isShutdown()) return;
        Map<Long, ChunkData> registrations = keptChunksByWorld.get(world.getName());
        if (registrations == null || registrations.isEmpty()) return;
        if (state.reconcile) {
            state.reconcile = false;
            store.forEachChunk(ChunkSection.getComponentType(), (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    sectionAdded(
                            store, chunk.getReferenceTo(i), chunk.getComponent(i, ChunkSection.getComponentType()));
                }
            });
        }
        for (long index : List.copyOf(registrations.keySet())) {
            Hold hold = hold(world, state, index);
            if (hold == null) continue;
            if (hold.columnRef != null) {
                WorldChunk column = hold.columnRef.isValid()
                        ? store.getComponent(hold.columnRef, WorldChunk.getComponentType())
                        : null;
                hold.column.set(column);
                if (column == null) hold.columnRef = null;
            }
            for (var entry : List.copyOf(hold.sections.entrySet())) {
                SectionHold owned = entry.getValue();
                ChunkSection section = owned.reference.isValid()
                        ? store.getComponent(owned.reference, ChunkSection.getComponentType())
                        : null;
                if (section == null
                        || section.getY() != entry.getKey()
                        || section.getX() != hold.registration.x
                        || section.getZ() != hold.registration.z) {
                    hold.sections.remove(entry.getKey());
                    owned.section.clear();
                    continue;
                }
                owned.section.set(section);
                section.resetActiveTimer();
                store.tryRemoveComponent(owned.reference, ChunkStore.REGISTRY.getNonTickingComponentType());
            }
            load(world, state, index, hold);
        }
        if (state.dirty) {
            save(world.getName());
            state.dirty = false;
        }
    }

    private ChunkData registration(World world, long index) {
        Map<Long, ChunkData> chunks = keptChunksByWorld.get(world.getName());
        return chunks == null ? null : chunks.get(index);
    }

    private void save(String worldName) {
        Map<Long, ChunkData> chunks = keptChunksByWorld.get(worldName);
        List<Snapshot> snapshots = new ArrayList<>();
        if (chunks != null) {
            for (ChunkData data : chunks.values()) {
                snapshots.add(new Snapshot(data.x, data.z, data.label, List.copyOf(data.sections)));
            }
        }
        persistence.submit(worldName, List.copyOf(snapshots));
    }

    private void writeSnapshot(String worldName, List<Snapshot> snapshot) throws IOException {
        Files.createDirectories(dataDir);
        Path destination = dataDir.resolve(worldName + ".json");
        if (snapshot.isEmpty()) {
            Files.deleteIfExists(destination);
            return;
        }
        Path temporary = Files.createTempFile(dataDir, "chunkloader-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(snapshot));
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record Snapshot(int x, int z, String label, List<Integer> sections) {}

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

    private static final class WorldState {
        private final Map<Long, Hold> holds = new HashMap<>();
        private boolean reconcile;
        private boolean dirty;

        private void clear() {
            holds.values().forEach(Hold::clear);
            holds.clear();
        }
    }

    private static final class Hold {
        private final ChunkData registration;
        private final OwnedKeepLoaded<WorldChunk> column =
                new OwnedKeepLoaded<>(WorldChunk::addKeepLoaded, WorldChunk::removeKeepLoaded);
        private Ref<ChunkStore> columnRef;
        private boolean loadingColumn;
        private long retryAfter;
        private final Map<Integer, SectionHold> sections = new HashMap<>();
        private final Set<Integer> requested = new HashSet<>();

        private Hold(ChunkData registration) {
            this.registration = registration;
        }

        private void clear() {
            sections.values().forEach(owned -> owned.section.clear());
            sections.clear();
            column.clear();
        }
    }

    private static final class SectionHold {
        private Ref<ChunkStore> reference;
        private final OwnedKeepLoaded<ChunkSection> section =
                new OwnedKeepLoaded<>(ChunkSection::addKeepLoaded, ChunkSection::removeKeepLoaded);
    }

    private static final class ChunkData {
        private int x, z;
        private String label;
        private Set<Integer> sections = new HashSet<>();

        private ChunkData(int x, int z, String label) {
            this.x = x;
            this.z = z;
            this.label = label != null ? label : "";
        }
    }
}
