package cc.irori.refixes.service;

import cc.irori.refixes.early.accessor.ChunkStoreAccess;
import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.metrics.MetricProvider;
import com.hypixel.hytale.metrics.MetricResults;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.events.StartWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkLoader;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class SharedInstanceStorage implements IChunkSaver.Cubic, MetricProvider {
    private static final CompletableFuture<Void> SAVED = CompletableFuture.completedFuture(null);
    private final IChunkSaver.Cubic saver;
    private final Store<ChunkStore> chunkStore;
    private final Map<Object, CompletableFuture<Void>> writes = new ConcurrentHashMap<>();
    private final Map<UUID, SectionKey> entityOrigins = new ConcurrentHashMap<>();

    private SharedInstanceStorage(IChunkSaver.Cubic saver, Store<ChunkStore> chunkStore) {
        this.saver = saver;
        this.chunkStore = chunkStore;
    }

    public static void registerEvents(JavaPlugin plugin) {
        plugin.getEventRegistry().registerGlobal(StartWorldEvent.class, event -> {
            if (!event.getWorld().getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) return;
            ChunkStore chunks = event.getWorld().getChunkStore();
            if (chunks.getSaver() instanceof SharedInstanceStorage) return;
            if (!(chunks.getSaver() instanceof IChunkSaver.Cubic cubic)
                    || !(chunks.getLoader() instanceof IChunkLoader.Cubic loader)) return;
            if (!(chunks instanceof ChunkStoreAccess access)) {
                throw new IllegalStateException("Shared-instance cubic storage requires Refixes early storage access");
            }
            SharedInstanceStorage storage = new SharedInstanceStorage(cubic, chunks.getStore());
            access.refixes$setLoader(storage.new Loader(loader));
            access.refixes$setSaver(storage);
        });
    }

    private record ColumnKey(int x, int z) {}

    private record SectionKey(int x, int y, int z, int lane) {}

    private CompletableFuture<Void> writeOnce(Object key, Supplier<CompletableFuture<Void>> operation) {
        CompletableFuture<Void> saved = writes.get(key);
        if (saved != null) return saved;
        CompletableFuture<Void> result = new CompletableFuture<>();
        CompletableFuture<Void> existing = writes.putIfAbsent(key, result);
        if (existing != null) return existing;
        try {
            operation.get().whenComplete((ignored, error) -> {
                if (error == null) result.complete(null);
                else {
                    writes.remove(key, result);
                    result.completeExceptionally(error);
                }
            });
        } catch (Throwable error) {
            writes.remove(key, result);
            result.completeExceptionally(error);
        }
        return result;
    }

    private static CompletableFuture<Void> completeIo(CompletableFuture<Void> future, Runnable onIoComplete) {
        if (onIoComplete != null) future.whenComplete((ignored, error) -> onIoComplete.run());
        return future;
    }

    private void loadedSection(int x, int y, int z, Holder<ChunkStore> holder) {
        if (holder == null) return;
        for (int lane = 0; lane < 3; lane++) writes.putIfAbsent(new SectionKey(x, y, z, lane), SAVED);
        EntitySection entities = holder.getComponent(EntitySection.getComponentType());
        if (entities != null) {
            for (Holder<EntityStore> entity : entities.getEntityHolders()) {
                UUIDComponent uuid = entity.getComponent(UUIDComponent.getComponentType());
                if (uuid != null) {
                    writes.putIfAbsent(uuid.getUuid(), SAVED);
                    entityOrigins.putIfAbsent(uuid.getUuid(), new SectionKey(x, y, z, 1));
                }
            }
        }
    }

    private void loadedColumn(int x, int z, Holder<ChunkStore> holder) {
        if (holder == null) return;
        WorldChunk chunk = holder.getComponent(WorldChunk.getComponentType());
        if (chunk != null && chunk.is(ChunkFlag.NEEDS_FORMAT_REWRITE)) return;
        writes.putIfAbsent(new ColumnKey(x, z), SAVED);
        ChunkColumn column = holder.getComponent(ChunkColumn.getComponentType());
        if (column != null && column.getSectionHolders() != null) {
            Holder<ChunkStore>[] sections = column.getSectionHolders();
            for (int y = 0; y < sections.length; y++) loadedSection(x, y, z, sections[y]);
        }
    }

    @Override
    public CompletableFuture<Void> saveChunkColumn(
            int x,
            int z,
            Store<ChunkStore> store,
            Ref<ChunkStore> ref,
            Executor completionExecutor,
            Runnable onIoComplete) {
        return completeIo(saveColumn(x, z, () -> store.copySerializableEntity(ref)), onIoComplete);
    }

    private void collectIdentities(int x, int y, int z, EntitySection entities, Map<Object, SectionKey> identities) {
        SectionKey origin = new SectionKey(x, y, z, 1);
        for (int lane = 0; lane < 3; lane++) identities.put(new SectionKey(x, y, z, lane), origin);
        if (entities == null) return;
        for (Holder<EntityStore> entity : entities.getEntityHolders()) {
            if (!entity.hasSerializableComponents(EntityStore.REGISTRY.getData())) continue;
            UUIDComponent uuid = entity.ensureAndGetComponent(UUIDComponent.getComponentType());
            identities.put(uuid.getUuid(), origin);
        }
        for (Ref<EntityStore> ref : entities.getEntityReferences()) {
            if (!ref.isValid()) continue;
            UUIDComponent uuid = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
            if (uuid != null) identities.put(uuid.getUuid(), origin);
        }
    }

    private CompletableFuture<Void> saveFresh(
            Map<Object, SectionKey> identities, Supplier<CompletableFuture<Void>> operation) {
        for (Object key : identities.keySet()) if (writes.containsKey(key)) return null;
        CompletableFuture<Void> result = new CompletableFuture<>();
        List<Object> claimed = new ArrayList<>();
        for (var entry : identities.entrySet()) {
            if (writes.putIfAbsent(entry.getKey(), result) != null) {
                for (Object key : claimed) writes.remove(key, result);
                result.completeExceptionally(
                        new IllegalStateException("Concurrent shared-instance first-write reservation"));
                return null;
            }
            claimed.add(entry.getKey());
        }
        for (var entry : identities.entrySet()) {
            if (entry.getKey() instanceof UUID uuid) entityOrigins.putIfAbsent(uuid, entry.getValue());
        }
        try {
            operation.get().whenComplete((ignored, error) -> {
                if (error == null) result.complete(null);
                else {
                    for (Object key : claimed) {
                        writes.remove(key, result);
                        if (key instanceof UUID uuid) entityOrigins.remove(uuid, identities.get(key));
                    }
                    result.completeExceptionally(error);
                }
            });
        } catch (Throwable error) {
            for (Object key : claimed) {
                writes.remove(key, result);
                if (key instanceof UUID uuid) entityOrigins.remove(uuid, identities.get(key));
            }
            result.completeExceptionally(error);
        }
        return result;
    }

    @Override
    public CompletableFuture<Void> saveSection(
            int x,
            int y,
            int z,
            Store<ChunkStore> store,
            Ref<ChunkStore> section,
            Executor completionExecutor,
            Runnable onIoComplete) {
        return completeIo(
                writeOnce(new SectionKey(x, y, z, 0), () -> saver.saveSection(x, y, z, store, section, null, null)),
                onIoComplete);
    }

    @Override
    public CompletableFuture<Void> saveEntitySection(
            int x,
            int y,
            int z,
            Store<ChunkStore> store,
            EntitySection section,
            Executor completionExecutor,
            Runnable onIoComplete) {
        SectionKey key = new SectionKey(x, y, z, 1);
        return completeIo(
                writeOnce(key, () -> {
                    EntitySection snapshot = new EntitySection();
                    List<CompletableFuture<Void>> bodies = new ArrayList<>();
                    List<UUID> claimed = new ArrayList<>();
                    CompletableFuture<Void> batch = new CompletableFuture<>();
                    try {
                        for (Holder<EntityStore> holder : section.getEntityHolders()) {
                            includeEntity(snapshot, holder, key, batch, claimed, bodies);
                        }
                        for (Ref<EntityStore> ref : section.getEntityReferences()) {
                            if (ref.isValid())
                                includeEntity(
                                        snapshot,
                                        ref.getStore().copySerializableEntity(ref),
                                        key,
                                        batch,
                                        claimed,
                                        bodies);
                        }
                        bodies.add(saver.saveEntitySection(x, y, z, store, snapshot, null, null));
                        CompletableFuture.allOf(bodies.toArray(CompletableFuture[]::new))
                                .whenComplete((ignored, error) -> {
                                    if (error == null) batch.complete(null);
                                    else {
                                        for (UUID uuid : claimed) {
                                            writes.remove(uuid, batch);
                                            entityOrigins.remove(uuid, key);
                                        }
                                        batch.completeExceptionally(error);
                                    }
                                });
                    } catch (Throwable error) {
                        for (UUID uuid : claimed) {
                            writes.remove(uuid, batch);
                            entityOrigins.remove(uuid, key);
                        }
                        batch.completeExceptionally(error);
                    }
                    return batch;
                }),
                onIoComplete);
    }

    private void includeEntity(
            EntitySection snapshot,
            Holder<EntityStore> source,
            SectionKey key,
            CompletableFuture<Void> batch,
            List<UUID> claimed,
            List<CompletableFuture<Void>> bodies) {
        if (!source.hasSerializableComponents(EntityStore.REGISTRY.getData())) return;
        UUIDComponent component = source.ensureAndGetComponent(UUIDComponent.getComponentType());
        UUID uuid = component.getUuid();
        SectionKey origin = entityOrigins.putIfAbsent(uuid, key);
        if (origin != null && !origin.equals(key)) return;
        CompletableFuture<Void> previous = writes.putIfAbsent(uuid, batch);
        Holder<EntityStore> holder;
        if (previous == null) {
            claimed.add(uuid);
            holder = source.cloneSerializable(EntityStore.REGISTRY.getData());
            holder.tryRemoveComponent(Dirty.getComponentType());
        } else {
            holder = EntityStore.REGISTRY.newHolder();
            holder.putComponent(UUIDComponent.getComponentType(), new UUIDComponent(uuid));
            Dirty dirty = new Dirty();
            dirty.consumeDirty();
            holder.putComponent(Dirty.getComponentType(), dirty);
            if (previous != batch) bodies.add(previous);
        }
        snapshot.loadEntityHolder(holder);
    }

    @Override
    public CompletableFuture<Void> saveBlockComponentSection(
            int x,
            int y,
            int z,
            Store<ChunkStore> store,
            BlockComponentSection section,
            Executor completionExecutor,
            Runnable onIoComplete) {
        return completeIo(
                writeOnce(new SectionKey(x, y, z, 2), () -> {
                    BlockComponentSection snapshot = (BlockComponentSection) section.cloneSerializable();
                    snapshot.markAllBlocksNeedsSaving();
                    return saver.saveBlockComponentSection(x, y, z, store, snapshot, null, null);
                }),
                onIoComplete);
    }

    @Override
    public CompletableFuture<Void> saveEntity(UUID uuid, Store<EntityStore> store, Ref<EntityStore> ref) {
        CompletableFuture<Void> existing = writes.get(uuid);
        if (existing != null) return existing;
        SectionKey entityKey = null;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) {
            var position = transform.getPosition();
            entityKey = new SectionKey(
                    ChunkUtil.chunkCoordinate((int) Math.floor(position.x)),
                    ChunkUtil.chunkCoordinate((int) Math.floor(position.y)),
                    ChunkUtil.chunkCoordinate((int) Math.floor(position.z)),
                    1);
            if (writes.containsKey(entityKey)) return SAVED;
            SectionKey origin = entityOrigins.putIfAbsent(uuid, entityKey);
            if (origin != null && !origin.equals(entityKey)) return SAVED;
        }
        SectionKey origin = entityKey;
        CompletableFuture<Void> result = writeOnce(uuid, () -> saver.saveEntity(uuid, store, ref));
        if (origin != null)
            result.whenComplete((ignored, error) -> {
                if (error != null) entityOrigins.remove(uuid, origin);
            });
        return result;
    }

    @Override
    public CompletableFuture<Void> saveHolder(int x, int z, Holder<ChunkStore> holder) {
        return saveColumn(x, z, holder::clone);
    }

    private CompletableFuture<Void> saveColumn(int x, int z, Supplier<Holder<ChunkStore>> snapshotSupplier) {
        return writeOnce(new ColumnKey(x, z), () -> {
            Holder<ChunkStore> snapshot = snapshotSupplier.get();
            ChunkColumn column = snapshot.getComponent(ChunkColumn.getComponentType());
            Map<Object, SectionKey> identities = new java.util.HashMap<>();
            if (column != null && column.getSectionHolders() != null) {
                Holder<ChunkStore>[] sections = column.getSectionHolders();
                for (int y = 0; y < sections.length; y++) {
                    if (sections[y] != null) {
                        collectIdentities(
                                x, y, z, sections[y].getComponent(EntitySection.getComponentType()), identities);
                    }
                }
            }
            CompletableFuture<Void> initial = saveFresh(identities, () -> saver.saveHolder(x, z, snapshot));
            if (initial != null) return initial;
            List<CompletableFuture<Void>> pending = new ArrayList<>();
            List<SectionKey> claimed = new ArrayList<>();
            CompletableFuture<Void> blocksWritten = new CompletableFuture<>();
            try {
                if (column != null && column.getSectionHolders() != null) {
                    Holder<ChunkStore>[] sections = column.getSectionHolders();
                    for (int y = 0; y < sections.length; y++) {
                        Holder<ChunkStore> section = sections[y];
                        if (section == null) continue;
                        EntitySection entities = section.getComponent(EntitySection.getComponentType());
                        if (entities != null) pending.add(saveEntitySection(x, y, z, chunkStore, entities, null, null));
                        BlockComponentSection blocks = section.getComponent(BlockComponentSection.getComponentType());
                        if (blocks != null)
                            pending.add(saveBlockComponentSection(x, y, z, chunkStore, blocks, null, null));
                        SectionKey key = new SectionKey(x, y, z, 0);
                        CompletableFuture<Void> existing = writes.putIfAbsent(key, blocksWritten);
                        if (existing != null) {
                            sections[y] = null;
                            pending.add(existing);
                        } else {
                            claimed.add(key);
                            section.tryRemoveComponent(EntitySection.getComponentType());
                            section.tryRemoveComponent(BlockComponentSection.getComponentType());
                        }
                    }
                }
                saver.saveHolder(x, z, snapshot).whenComplete((ignored, error) -> {
                    if (error == null) blocksWritten.complete(null);
                    else {
                        for (SectionKey key : claimed) writes.remove(key, blocksWritten);
                        blocksWritten.completeExceptionally(error);
                    }
                });
            } catch (Throwable error) {
                for (SectionKey key : claimed) writes.remove(key, blocksWritten);
                blocksWritten.completeExceptionally(error);
            }
            pending.add(blocksWritten);
            return CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new));
        });
    }

    @Override
    public CompletableFuture<Void> removeHolder(int x, int z) {
        return SAVED;
    }

    @Override
    public CompletableFuture<Void> removeEntity(UUID uuid) {
        return SAVED;
    }

    @Override
    public LongList getIndexes() throws IOException {
        return saver.getIndexes();
    }

    @Override
    public void flush() throws IOException {
        saver.flush();
    }

    @Override
    public CompletableFuture<Void> compact(long[] removedHint) {
        return saver.compact(removedHint);
    }

    @Override
    public void pauseBackgroundSaving(ChunkSavingSystems.Data data) {
        saver.pauseBackgroundSaving(data);
    }

    @Override
    public CompletableFuture<Void> resumeBackgroundSaving() {
        return saver.resumeBackgroundSaving();
    }

    @Override
    public MetricResults toMetricResults() {
        return saver instanceof MetricProvider metrics ? metrics.toMetricResults() : null;
    }

    @Override
    public void close() throws IOException {
        saver.close();
        writes.clear();
        entityOrigins.clear();
    }

    private final class Loader implements IChunkLoader.Cubic, MetricProvider {
        private final IChunkLoader.Cubic loader;

        private Loader(IChunkLoader.Cubic loader) {
            this.loader = loader;
        }

        @Override
        public MetricResults toMetricResults() {
            return loader instanceof MetricProvider metrics ? metrics.toMetricResults() : null;
        }

        @Override
        public CompletableFuture<Holder<ChunkStore>> loadHolder(int x, int z) {
            return loader.loadHolder(x, z).thenApply(holder -> {
                loadedColumn(x, z, holder);
                return holder;
            });
        }

        @Override
        public CompletableFuture<Holder<ChunkStore>> loadSectionHolder(int x, int y, int z) {
            return loader.loadSectionHolder(x, y, z).thenApply(holder -> {
                loadedSection(x, y, z, holder);
                return holder;
            });
        }

        @Override
        public LongList getIndexes() throws IOException {
            return loader.getIndexes();
        }

        @Override
        public void close() throws IOException {
            loader.close();
        }
    }
}
