package cc.irori.refixes.service;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@IfHytale("<0.8.0-pre.1")
public final class SharedInstanceStorageV07 extends SharedInstanceStorage {

    public SharedInstanceStorageV07(IChunkSaver.Cubic saver, Store<ChunkStore> chunkStore) {
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
    public CompletableFuture<Void> removeEntity(UUID uuid) {
        return SAVED;
    }
}
