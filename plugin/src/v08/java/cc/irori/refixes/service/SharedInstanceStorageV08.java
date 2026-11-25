package cc.irori.refixes.service;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
public final class SharedInstanceStorageV08 extends SharedInstanceStorage {

    public SharedInstanceStorageV08(IChunkSaver.Cubic saver, Store<ChunkStore> chunkStore) {
        super(saver, chunkStore);
    }

    @Override
    void includeEntity(
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
        if (previous != null) {
            if (previous != batch) bodies.add(previous);
            return;
        }
        claimed.add(uuid);
        Holder<EntityStore> holder = source.cloneSerializable(EntityStore.REGISTRY.getData());
        holder.tryRemoveComponent(Dirty.getComponentType());
        snapshot.loadEntityHolder(holder);
    }

    @Override
    public CompletableFuture<Void> saveEntity(UUID uuid, Store<EntityStore> store, Ref<EntityStore> ref) {
        CompletableFuture<Void> existing = writes.get(uuid);
        if (existing != null) return existing;
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        Ref<ChunkStore> sectionRef = transform == null ? null : transform.getSectionRef();
        ChunkSection section = sectionRef == null || !sectionRef.isValid()
                ? null
                : sectionRef.getStore().getComponent(sectionRef, ChunkSection.getComponentType());
        if (section == null) return SAVED;
        SectionKey origin = new SectionKey(section.getX(), section.getY(), section.getZ(), 1);
        if (writes.containsKey(origin)) return SAVED;
        SectionKey claimed = entityOrigins.putIfAbsent(uuid, origin);
        if (claimed != null && !claimed.equals(origin)) return SAVED;
        CompletableFuture<Void> result = writeOnce(uuid, () -> saver.saveEntity(uuid, store, ref));
        result.whenComplete((ignored, error) -> {
            if (error != null) entityOrigins.remove(uuid, origin);
        });
        return result;
    }

    @Override
    public CompletableFuture<Void> saveSectionHolder(int x, int y, int z, Holder<ChunkStore> holder) {
        Map<Object, SectionKey> identities = new HashMap<>();
        collectIdentities(x, y, z, holder.getComponent(EntitySection.getComponentType()), identities);
        CompletableFuture<Void> saved = saveFresh(identities, () -> saver.saveSectionHolder(x, y, z, holder));
        if (saved != null) return saved;
        holder.tryRemoveComponent(EntitySection.getComponentType());
        holder.tryRemoveComponent(BlockComponentSection.getComponentType());
        return SAVED;
    }

    @Override
    public CompletableFuture<Void> removeSection(int x, int y, int z) {
        return SAVED;
    }

    @Override
    public CompletableFuture<Void> removeEntity(UUID uuid, Object storageState) {
        return SAVED;
    }

    @Override
    public CompletableFuture<Void> removeEntityMembership(UUID uuid, Object storageState) {
        return SAVED;
    }
}
