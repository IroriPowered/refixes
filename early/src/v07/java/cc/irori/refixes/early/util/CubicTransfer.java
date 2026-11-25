package cc.irori.refixes.early.util;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.duck.CubicTransferSection;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.BufferChunkSaver;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import com.hypixel.hytale.server.core.universe.world.storage.provider.IndexedStorageChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.storage.provider.RocksDbChunkStorageProvider;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

@IfHytale("<0.8.0-pre.1")
public final class CubicTransfer {

    private static final HytaleLogger LOGGER = Logs.logger();

    private CubicTransfer() {}

    @IfHytale("<0.8.0-pre.1")
    public static final class Pending {
        private final World target;
        private final EntitySection section;
        private Ref<EntityStore> ref;
        private Holder<EntityStore> holder;
        private Dirty dirty;
        private final UUID uuid;
        private final WeakReference<World> source;
        private final WeakReference<Ref<ChunkStore>> sourceSection;
        private final List<Source> chained;
        private boolean bodyDurable;
        private boolean indexDurable;
        private volatile boolean closed;
        private final List<Departure> departures;

        private Pending(
                World target,
                EntitySection section,
                Ref<EntityStore> ref,
                Dirty dirty,
                UUID uuid,
                World source,
                Ref<ChunkStore> sourceSection,
                List<Source> chained,
                List<Departure> departures) {
            this.target = target;
            this.section = section;
            this.ref = ref;
            this.dirty = dirty;
            this.uuid = uuid;
            this.source = new WeakReference<>(source);
            this.sourceSection = new WeakReference<>(sourceSection);
            this.chained = chained;
            this.departures = departures;
        }

        private Dirty dirty() {
            return holder != null ? holder.getComponent(Dirty.getComponentType()) : dirty;
        }

        private boolean serializable() {
            if (holder != null) return holder.getArchetype().hasSerializableComponents(EntityStore.REGISTRY.getData());
            Store<EntityStore> store = ref.getStore();
            return store.getArchetype(ref)
                    .hasSerializableComponents(store.getRegistry().getData());
        }

        private boolean current() {
            if (closed) return false;
            if (holder != null) {
                for (Holder<EntityStore> h : section.getEntityHolders()) if (h == holder) return true;
                return false;
            }
            if (!ref.isValid() || ref.getStore() != target.getEntityStore().getStore()) return false;
            if (!section.getEntityReferences().contains(ref)) return false;
            return target.getEntityStore().getStore().getComponent(ref, Dirty.getComponentType()) == dirty;
        }
    }

    @IfHytale("<0.8.0-pre.1")
    public record Snapshot(Pending pending, boolean bodyCandidate) {}

    @IfHytale("<0.8.0-pre.1")
    public record Source(
            WeakReference<World> world, WeakReference<Ref<ChunkStore>> section, List<Departure> departures) {}

    private static List<Departure> carried(Departure own, List<Source> sources) {
        List<Departure> all = new ArrayList<>(1);
        if (own != null) all.add(own);
        for (Source s : sources) for (Departure d : s.departures) if (!all.contains(d)) all.add(d);
        return all.isEmpty() ? List.of() : List.copyOf(all);
    }

    private static boolean covers(List<Departure> departures, World world) {
        for (Departure d : departures) if (d.source.get() == world) return true;
        return false;
    }

    private static void settle(List<Departure> departures) {
        for (Departure d : departures) d.acked = true;
    }

    @IfHytale("<0.8.0-pre.1")
    public static final class Departure {
        private final UUID uuid;
        private final WeakReference<World> source;
        private final WeakReference<Ref<ChunkStore>> section;
        private volatile boolean dropped;
        private volatile boolean acked;
        private volatile boolean retired;

        private Departure(UUID uuid, World source, Ref<ChunkStore> section) {
            this.uuid = uuid;
            this.source = new WeakReference<>(source);
            this.section = new WeakReference<>(section);
        }

        private boolean done() {
            if (dropped || retired) return true;
            World world = source.get();
            if (world == null) return true;
            Ref<EntityStore> live = world.getEntityStore().getRefFromUUID(uuid);
            return live != null && live.isValid();
        }

        private Ref<ChunkStore> retire(World world) {
            if (retired || !acked) return null;
            retired = true;
            Ref<ChunkStore> sectionRef = section.get();
            if (!CubicTransfer.retire(world, sectionRef, uuid) || sectionRef == null || !sectionRef.isValid())
                return null;
            ChunkSection chunkSection = sectionRef.getStore().getComponent(sectionRef, ChunkSection.getComponentType());
            return chunkSection == null ? null : chunkSection.getChunkColumnReference();
        }
    }

    private static final Map<Store<ChunkStore>, Set<Departure>> DEPARTURES =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static Departure depart(World source, World target, Ref<ChunkStore> section, UUID uuid) {
        IChunkSaver into = target.getChunkStore().getSaver();
        if (!nativeColumnSaver(source.getChunkStore().getSaver())
                || !(nativeColumnSaver(into) || into instanceof IChunkSaver.Cubic)) return null;
        Departure departure = new Departure(uuid, source, section);
        Set<Departure> departures =
                DEPARTURES.computeIfAbsent(source.getChunkStore().getStore(), key -> ConcurrentHashMap.newKeySet());
        departures.removeIf(Departure::done);
        departures.add(departure);
        return departure;
    }

    public static CompletableFuture<Void> finalSave(
            World world, Store<ChunkStore> store, CompletableFuture<Void> first) {
        Set<Departure> departures = DEPARTURES.remove(store);
        if (departures == null || EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.get() <= 0 || !world.isInThread())
            return first;
        Throwable failure = null;
        try {
            first.join();
        } catch (CompletionException e) {
            if (!recoverable(e)) throw e;
            failure = e;
        }
        Set<Ref<ChunkStore>> columns = new HashSet<>();
        int unconfirmed = 0;
        for (Departure d : departures) {
            if (d.done()) continue;
            Ref<ChunkStore> column = d.retire(world);
            if (column != null) columns.add(column);
            if (!d.done()) unconfirmed++;
        }
        if (unconfirmed != 0)
            LOGGER.atWarning().log(
                    "%d entities that left %s have no confirmed save elsewhere yet; their saved copies remain there",
                    unconfirmed, world.getName());
        if (columns.isEmpty()) return first;
        ChunkSavingSystems.Data data = store.getResource(ChunkStore.SAVE_RESOURCE);
        for (Ref<ChunkStore> column : columns)
            if (column.isValid()) ChunkSavingSystems.saveChunk(column, data, true, store, null);
        CompletableFuture<Void> saving = data.waitForSavingChunks();
        return failure == null ? saving : CompletableFuture.allOf(first, saving);
    }

    private static boolean recoverable(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause())
            if (t instanceof CancellationException || t instanceof Error) return false;
        return true;
    }

    public static void stopped(Store<ChunkStore> store) {
        DEPARTURES.remove(store);
    }

    @IfHytale("<0.8.0-pre.1")
    private static final class Fenced extends WeakReference<EntitySection> {
        private final UUID uuid;
        private final WeakReference<World> world;

        private Fenced(EntitySection section, UUID uuid, World world) {
            super(section, CLEARED);
            this.uuid = uuid;
            this.world = new WeakReference<>(world);
        }
    }

    private static final ReferenceQueue<EntitySection> CLEARED = new ReferenceQueue<>();

    private static final Map<UUID, List<Fenced>> FENCED = new ConcurrentHashMap<>();

    private static void expunge() {
        for (Reference<? extends EntitySection> r; (r = CLEARED.poll()) != null; ) {
            Fenced stale = (Fenced) r;
            FENCED.computeIfPresent(stale.uuid, (key, list) -> {
                List<Fenced> next = new ArrayList<>(list.size());
                for (Fenced f : list) if (f != stale) next.add(f);
                return next.isEmpty() ? null : next;
            });
        }
    }

    public static List<Source> chain(
            Store<ChunkStore> chunks, Ref<ChunkStore> sectionRef, Ref<EntityStore> ref, UUID uuid) {
        return chain(chunks.getComponent(sectionRef, EntitySection.getComponentType()), ref, uuid);
    }

    public static List<Source> chain(EntitySection section, Ref<EntityStore> ref, UUID uuid) {
        if (!(section instanceof CubicTransferSection holder)) return List.of();
        List<Pending> pending = holder.refixes$peekTransfers();
        if (pending == null || pending.isEmpty()) return List.of();
        List<Source> sources = new ArrayList<>();
        for (Pending p : pending) {
            if (p.ref != ref || !p.uuid.equals(uuid)) continue;
            synchronized (p) {
                if (p.closed) continue;
            }
            add(sources, p.source, p.sourceSection, p.departures);
            for (Source s : p.chained) add(sources, s.world, s.section, s.departures);
        }
        return sources;
    }

    private static void add(
            List<Source> sources,
            WeakReference<World> world,
            WeakReference<Ref<ChunkStore>> section,
            List<Departure> departures) {
        World w = world.get();
        Ref<ChunkStore> s = section.get();
        if (w == null) return;
        for (int i = 0; i < sources.size(); i++) {
            Source e = sources.get(i);
            if (e.world.get() == w && e.section.get() == s) {
                if (!departures.isEmpty())
                    sources.set(
                            i,
                            new Source(
                                    e.world,
                                    e.section,
                                    carried(null, List.of(e, new Source(world, section, departures)))));
                return;
            }
        }
        sources.add(new Source(world, section, departures));
    }

    public static void fence(Store<ChunkStore> chunks, Ref<ChunkStore> sectionRef, UUID uuid) {
        EntitySection section = chunks.getComponent(sectionRef, EntitySection.getComponentType());
        if (!(section instanceof CubicTransferSection holder)
                || !holder.refixes$ensureFence().add(uuid)) return;
        expunge();
        World world = chunks.getExternalData().getWorld();
        FENCED.compute(uuid, (key, list) -> {
            List<Fenced> next = list == null ? new ArrayList<>(1) : new ArrayList<>(list);
            next.add(new Fenced(section, uuid, world));
            return next;
        });
    }

    private static void unfence(EntitySection section, UUID uuid) {
        if (section instanceof CubicTransferSection holder) {
            Set<UUID> fence = holder.refixes$peekFence();
            if (fence != null) fence.remove(uuid);
        }
        FENCED.computeIfPresent(uuid, (key, list) -> {
            List<Fenced> next = new ArrayList<>(list.size());
            for (Fenced f : list) {
                EntitySection s = f.get();
                if (s != null && s != section) next.add(f);
            }
            return next.isEmpty() ? null : next;
        });
    }

    private static void retireWorld(World world, UUID uuid) {
        expunge();
        List<Fenced> list = FENCED.get(uuid);
        if (list == null) return;
        for (Fenced f : list) {
            EntitySection s = f.get();
            if (s == null || f.world.get() != world) continue;
            unfence(s, uuid);
            s.markNeedsSaving();
        }
    }

    public static void reentered(EntitySection section, Ref<EntityStore> ref) {
        if (!(section instanceof CubicTransferSection holder)) return;
        Set<UUID> fence = holder.refixes$peekFence();
        if (fence == null || fence.isEmpty() || !ref.isValid()) return;
        UUIDComponent uuid = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        if (uuid != null && fence.contains(uuid.getUuid())) unfence(section, uuid.getUuid());
    }

    public static List<Source> removing(EntitySection section, Ref<EntityStore> ref) {
        if (!ref.isValid()) return List.of();
        UUIDComponent uuid = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        return uuid == null ? List.of() : chain(section, ref, uuid.getUuid());
    }

    public static void removed(World world, UUID uuid, List<Source> sources) {
        retireWorld(world, uuid);
        List<Departure> departures = carried(null, sources);
        settle(departures);
        for (Source s : sources) {
            World source = s.world.get();
            Ref<ChunkStore> section = s.section.get();
            if (source == null || covers(departures, source)) continue;
            try {
                source.execute(() -> {
                    if (!retire(source, section, uuid))
                        LOGGER.atWarning().log(
                                "Entity %s was removed in %s but its source section in %s has unloaded; its saved copy remains there",
                                uuid, world.getName(), source.getName());
                });
            } catch (RuntimeException e) {
                LOGGER.atWarning().withCause(e).log(
                        "Entity %s was removed in %s but %s stopped accepting tasks; its saved copy remains there",
                        uuid, world.getName(), source.getName());
            }
        }
    }

    private static boolean retire(World source, Ref<ChunkStore> section, UUID uuid) {
        ChunkStore chunks = source.getChunkStore();
        if (section == null || !section.isValid() || section.getStore() != chunks.getStore())
            return dropUnloaded(source, uuid);
        EntitySection entities = chunks.getStore().getComponent(section, EntitySection.getComponentType());
        if (entities == null) return true;
        unfence(entities, uuid);
        retireWorld(source, uuid);
        entities.markNeedsSaving();
        return true;
    }

    private static boolean dropUnloaded(World source, UUID uuid) {
        ChunkStore chunks = source.getChunkStore();
        IChunkSaver saver = chunks.getSaver();
        if (saver == null
                || saver.getClass() != RocksDbChunkStorageProvider.Saver.class
                || chunks.getStore().isShutdown()) return false;
        Ref<EntityStore> live = source.getEntityStore().getRefFromUUID(uuid);
        if (live != null && live.isValid()) return true;
        retireWorld(source, uuid);
        try {
            ((RocksDbChunkStorageProvider.Saver) saver).removeEntity(uuid).whenComplete((ignored, e) -> {
                if (e != null) logRemovalFailure(source, uuid, e);
            });
        } catch (RuntimeException e) {
            logRemovalFailure(source, uuid, e);
        }
        return true;
    }

    private static void logRemovalFailure(World source, UUID uuid, Throwable e) {
        LOGGER.atWarning().withCause(e).log(
                "Entity %s left %s but its saved copy there could not be removed", uuid, source.getName());
    }

    public static void retain(Store<ChunkStore> store, EntitySection section, List<UUID> uuids) {
        if (!(section instanceof CubicTransferSection holder)) return;
        Set<UUID> fence = holder.refixes$peekFence();
        if (fence == null || fence.isEmpty()) return;
        EntityStore entities = store.getExternalData().getWorld().getEntityStore();
        for (UUID uuid : fence) {
            if (uuids.contains(uuid)) continue;
            Ref<EntityStore> ref = entities.getRefFromUUID(uuid);
            if (ref != null && ref.isValid()) continue;
            uuids.add(uuid);
        }
    }

    public static void arrived(
            World target,
            UUID uuid,
            World source,
            Ref<ChunkStore> sourceSection,
            List<Source> chained,
            Departure departure) {
        List<Departure> departures = carried(departure, chained);
        if (arrived(target, uuid, source, sourceSection, chained, departures) == null)
            for (Departure d : departures) d.dropped = true;
    }

    @IfHytale("<0.8.0-pre.1")
    private record Leaving(UUID uuid, List<Source> chained) {}

    private static final ThreadLocal<Leaving> LEAVING = new ThreadLocal<>();

    public static Object enterMove() {
        Leaving previous = LEAVING.get();
        LEAVING.remove();
        return previous;
    }

    public static void exitMove(Object previous) {
        if (previous instanceof Leaving l) LEAVING.set(l);
        else LEAVING.remove();
    }

    public static void leaving(UUID uuid, List<Source> chained) {
        if (carried(null, chained).isEmpty()) LEAVING.remove();
        else LEAVING.set(new Leaving(uuid, chained));
    }

    public static void restored(World world, UUID uuid) {
        Leaving l = LEAVING.get();
        LEAVING.remove();
        if (l == null || !l.uuid.equals(uuid)) return;
        List<Source> chained = l.chained;
        List<Departure> departures = carried(null, chained);
        if (departures.isEmpty()) return;
        Ref<EntityStore> live = world.getEntityStore().getRefFromUUID(uuid);
        if (live == null || !live.isValid()) return;
        if (arrived(world, uuid, null, null, chained, departures) == null)
            for (Departure d : departures) d.dropped = true;
    }

    private static Pending arrived(
            World target,
            UUID uuid,
            World source,
            Ref<ChunkStore> sourceSection,
            List<Source> chained,
            List<Departure> departures) {
        Store<EntityStore> store = target.getEntityStore().getStore();
        Ref<EntityStore> ref = target.getEntityStore().getRefFromUUID(uuid);
        if (ref == null || !ref.isValid()) return null;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Dirty dirty = store.getComponent(ref, Dirty.getComponentType());
        Ref<ChunkStore> sectionRef = transform == null ? null : transform.getSectionRef();
        ChunkStore chunks = target.getChunkStore();
        if (dirty == null || sectionRef == null || !sectionRef.isValid() || sectionRef.getStore() != chunks.getStore())
            return null;
        EntitySection section = chunks.getStore().getComponent(sectionRef, EntitySection.getComponentType());
        if (!(section instanceof CubicTransferSection holder)
                || !section.getEntityReferences().contains(ref)) return null;
        if (sourceSection == null) {
            if (chained.isEmpty()) return null;
            Source first = chained.getFirst();
            source = first.world.get();
            sourceSection = first.section.get();
            chained = chained.subList(1, chained.size());
            if (source == null) return null;
        }
        List<Pending> pending = holder.refixes$ensureTransfers();
        prune(pending, uuid);
        Pending p = new Pending(target, section, ref, dirty, uuid, source, sourceSection, chained, departures);
        pending.add(p);
        return p;
    }

    public static void moved(
            World world, EntitySection section, Ref<EntityStore> ref, Dirty dirty, UUID uuid, List<Source> chained) {
        if (chained.isEmpty()
                || dirty == null
                || !world.isInThread()
                || !ref.isValid()
                || ref.getStore() != world.getEntityStore().getStore()
                || !(section instanceof CubicTransferSection holder)
                || !section.getEntityReferences().contains(ref)) return;
        Source first = chained.getFirst();
        World source = first.world.get();
        Ref<ChunkStore> sourceSection = first.section.get();
        if (source == null) return;
        List<Pending> pending = holder.refixes$ensureTransfers();
        prune(pending, uuid);
        pending.add(new Pending(
                world,
                section,
                ref,
                dirty,
                uuid,
                source,
                sourceSection,
                chained.subList(1, chained.size()),
                carried(null, chained)));
    }

    public static void movedHolder(
            World world, EntitySection section, Holder<EntityStore> holder, UUID uuid, List<Source> chained) {
        Dirty dirty = holder.getComponent(Dirty.getComponentType());
        if (chained.isEmpty() || !world.isInThread() || !(section instanceof CubicTransferSection target)) return;
        Source first = chained.getFirst();
        World source = first.world.get();
        Ref<ChunkStore> sourceSection = first.section.get();
        if (source == null) return;
        List<Pending> pending = target.refixes$ensureTransfers();
        prune(pending, uuid);
        Pending p = new Pending(
                world,
                section,
                null,
                dirty,
                uuid,
                source,
                sourceSection,
                chained.subList(1, chained.size()),
                carried(null, chained));
        p.holder = holder;
        pending.add(p);
    }

    public static void parked(List<Pending> pending, Holder<EntityStore> holder) {
        Dirty dirty = holder.getComponent(Dirty.getComponentType());
        UUIDComponent uuid = holder.getComponent(UUIDComponent.getComponentType());
        if (uuid == null) return;
        for (Pending p : pending) {
            if (p.holder == null && !p.closed && p.uuid.equals(uuid.getUuid())) {
                p.ref = null;
                p.holder = holder;
                p.dirty = dirty;
            }
        }
    }

    public static void resumed(List<Pending> pending, Ref<EntityStore> ref) {
        if (!ref.isValid()) return;
        Dirty dirty = ref.getStore().getComponent(ref, Dirty.getComponentType());
        UUIDComponent uuid = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
        if (dirty == null || uuid == null) return;
        for (Pending p : pending) {
            if (p.holder != null && !p.closed && p.uuid.equals(uuid.getUuid())) {
                p.holder = null;
                p.ref = ref;
                p.dirty = dirty;
            }
        }
    }

    public static List<Snapshot> beforeSectionSave(Store<ChunkStore> store, EntitySection section) {
        if (!(section instanceof CubicTransferSection holder)) return List.of();
        List<Pending> pending = holder.refixes$peekTransfers();
        if (pending == null
                || pending.isEmpty()
                || !store.getExternalData().getWorld().isInThread()) return List.of();
        prune(pending, null);
        List<Snapshot> snapshots = new ArrayList<>(pending.size());
        for (Pending p : pending)
            snapshots.add(new Snapshot(
                    p,
                    p.dirty() == null
                            ? p.holder != null
                            : p.dirty().isDirty() && !p.dirty().isSaving()));
        return snapshots;
    }

    public static CompletableFuture<Void> afterSectionSave(List<Snapshot> snapshots, CompletableFuture<Void> future) {
        if (snapshots.isEmpty() || future == null) return future;
        List<Pending> bodies = new ArrayList<>();
        List<Pending> indexes = new ArrayList<>(snapshots.size());
        for (Snapshot s : snapshots) {
            indexes.add(s.pending);
            if (s.bodyCandidate
                    && (s.pending.dirty() == null || s.pending.dirty().isSaving())) bodies.add(s.pending);
        }
        return future.whenComplete((ignored, failure) -> {
            for (Pending p : indexes) complete(p, failure == null, bodies.contains(p), true);
        });
    }

    public static List<Pending> beforeColumnSave(IChunkSaver saver, Store<ChunkStore> store, Ref<ChunkStore> chunk) {
        if (!nativeColumnSaver(saver) || !store.getExternalData().getWorld().isInThread() || !chunk.isValid())
            return List.of();
        ChunkColumn column = store.getComponent(chunk, ChunkColumn.getComponentType());
        if (column == null) return List.of();
        List<Pending> captured = null;
        for (Ref<ChunkStore> sectionRef : column.getSections()) {
            if (sectionRef == null || !sectionRef.isValid()) continue;
            EntitySection section = store.getComponent(sectionRef, EntitySection.getComponentType());
            if (!(section instanceof CubicTransferSection holder)) continue;
            List<Pending> pending = holder.refixes$peekTransfers();
            if (pending == null || pending.isEmpty()) continue;
            prune(pending, null);
            for (Pending p : pending) {
                if (!p.serializable()) continue;
                if (captured == null) captured = new ArrayList<>(pending.size());
                captured.add(p);
            }
        }
        return captured == null ? List.of() : captured;
    }

    private static final ClassValue<Boolean> NATIVE_COLUMN_SAVERS = new NativeColumnSavers();

    @IfHytale("<0.8.0-pre.1")
    private static final class NativeColumnSavers extends ClassValue<Boolean> {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (!BufferChunkSaver.class.isAssignableFrom(type) || IChunkSaver.Cubic.class.isAssignableFrom(type))
                return false;
            try {
                Method column = type.getMethod(
                        "saveChunkColumn",
                        int.class,
                        int.class,
                        Store.class,
                        Ref.class,
                        Executor.class,
                        Runnable.class);
                return (column.getDeclaringClass() == IChunkSaver.class || orderedIndexedColumn(column))
                        && type.getMethod("saveHolder", int.class, int.class, Holder.class)
                                        .getDeclaringClass()
                                == BufferChunkSaver.class;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    }

    private static boolean nativeColumnSaver(IChunkSaver saver) {
        return saver != null && NATIVE_COLUMN_SAVERS.get(saver.getClass());
    }

    private static boolean orderedIndexedColumn(Method column) {
        if (column.getDeclaringClass() != IndexedStorageChunkStorageProvider.IndexedStorageChunkSaver.class)
            return false;
        MixinMerged merged = column.getAnnotation(MixinMerged.class);
        return merged != null && merged.mixin().equals("cc.irori.refixes.early.mixin.MixinIndexedColumnSaveOrder");
    }

    public static CompletableFuture<Void> afterColumnSave(List<Pending> captured, CompletableFuture<Void> future) {
        if (captured.isEmpty() || future == null) return future;
        return future.whenComplete((ignored, failure) -> {
            for (Pending p : captured) complete(p, failure == null, true, true);
        });
    }

    public static Pending beforeEntitySave(Store<EntityStore> store, Ref<EntityStore> ref, UUID uuid) {
        World world = store.getExternalData().getWorld();
        if (!world.isInThread() || !ref.isValid()) return null;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Ref<ChunkStore> sectionRef = transform == null ? null : transform.getSectionRef();
        if (sectionRef == null || !sectionRef.isValid()) return null;
        EntitySection section =
                world.getChunkStore().getStore().getComponent(sectionRef, EntitySection.getComponentType());
        if (!(section instanceof CubicTransferSection holder)) return null;
        List<Pending> pending = holder.refixes$peekTransfers();
        if (pending == null) return null;
        for (Pending p : pending) {
            if (p.ref == ref && p.uuid.equals(uuid) && p.current() && p.dirty.isDirty() && !p.dirty.isSaving())
                return p;
        }
        return null;
    }

    public static CompletableFuture<Void> afterEntitySave(Pending pending, CompletableFuture<Void> future) {
        if (pending == null || future == null) return future;
        return future.whenComplete((ignored, failure) -> complete(pending, failure == null, true, false));
    }

    public static void left(List<Pending> pending, Ref<EntityStore> ref) {
        for (Pending p : pending) {
            if (p.ref == ref) {
                synchronized (p) {
                    p.closed = true;
                }
            }
        }
    }

    private static void complete(Pending p, boolean success, boolean body, boolean index) {
        boolean ready;
        synchronized (p) {
            if (p.closed || !success) return;
            if (body) p.bodyDurable = true;
            if (index) p.indexDurable = true;
            ready = p.bodyDurable && p.indexDurable;
        }
        if (!ready) return;
        try {
            p.target.execute(() -> release(p));
        } catch (RuntimeException e) {
            boolean first;
            synchronized (p) {
                first = !p.closed;
                p.closed = true;
            }
            if (first) {
                LOGGER.atWarning().withCause(e).log(
                        "Entity %s is saved in %s but that world stopped accepting tasks; its saved copy in the world it came from remains there",
                        p.uuid, p.target.getName());
            }
        }
    }

    private static void release(Pending p) {
        synchronized (p) {
            boolean current = p.current();
            p.closed = true;
            if (!current) return;
        }
        settle(p.departures);
        World source = p.source.get();
        if (!covers(p.departures, source)) markSource(p, source, p.sourceSection.get());
        for (Source s : p.chained) {
            World chained = s.world.get();
            if (!covers(p.departures, chained)) markSource(p, chained, s.section.get());
        }
    }

    private static void markSource(Pending p, World source, Ref<ChunkStore> section) {
        if (source == null) return;
        try {
            source.execute(() -> {
                if (!retire(source, section, p.uuid))
                    LOGGER.atWarning().log(
                            "Entity %s is saved in %s but its source section in %s has unloaded; its saved copy remains there",
                            p.uuid, p.target.getName(), source.getName());
            });
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log(
                    "Entity %s is saved in %s but %s stopped accepting tasks; its saved copy remains there",
                    p.uuid, p.target.getName(), source.getName());
        }
    }

    private static void prune(List<Pending> pending, UUID replaced) {
        Iterator<Pending> it = pending.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            boolean drop;
            synchronized (p) {
                drop = p.closed || (replaced != null && replaced.equals(p.uuid));
            }
            if (drop || !p.current()) {
                synchronized (p) {
                    p.closed = true;
                }
                it.remove();
            }
        }
    }
}
