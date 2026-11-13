package cc.irori.refixes.system;

import cc.irori.refixes.service.ChunkLoaderService;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.component.system.StoreSystem;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

public final class ChunkLoaderSectionLifecycleSystem extends RefSystem<ChunkStore> {
    private final ChunkLoaderService service;

    public ChunkLoaderSectionLifecycleSystem(ChunkLoaderService service) {
        this.service = service;
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return ChunkSection.getComponentType();
    }

    @Override
    public void onEntityAdded(
            Ref<ChunkStore> ref, AddReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> buffer) {
        service.sectionAdded(store, ref, buffer.getComponent(ref, ChunkSection.getComponentType()));
    }

    @Override
    public void onEntityRemove(
            Ref<ChunkStore> ref, RemoveReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> buffer) {
        service.sectionRemoved(store, ref, buffer.getComponent(ref, ChunkSection.getComponentType()));
    }

    public static final class Changes extends RefChangeSystem<ChunkStore, ChunkSection> {
        private final ChunkLoaderService service;

        public Changes(ChunkLoaderService service) {
            this.service = service;
        }

        @Override
        public Query<ChunkStore> getQuery() {
            return ChunkSection.getComponentType();
        }

        @Override
        public ComponentType<ChunkStore, ChunkSection> componentType() {
            return ChunkSection.getComponentType();
        }

        @Override
        public void onComponentAdded(
                Ref<ChunkStore> ref, ChunkSection section, Store<ChunkStore> store, CommandBuffer<ChunkStore> buffer) {
            service.sectionAdded(store, ref, section);
        }

        @Override
        public void onComponentSet(
                Ref<ChunkStore> ref,
                ChunkSection previous,
                ChunkSection section,
                Store<ChunkStore> store,
                CommandBuffer<ChunkStore> buffer) {
            if (previous == section) return;
            service.sectionRemoved(store, ref, previous);
            service.sectionAdded(store, ref, section);
        }

        @Override
        public void onComponentRemoved(
                Ref<ChunkStore> ref, ChunkSection section, Store<ChunkStore> store, CommandBuffer<ChunkStore> buffer) {
            service.sectionRemoved(store, ref, section);
        }
    }

    public static final class Stores extends StoreSystem<ChunkStore> {
        private final ChunkLoaderService service;

        public Stores(ChunkLoaderService service) {
            this.service = service;
        }

        @Override
        public void onSystemAddedToStore(Store<ChunkStore> store) {}

        @Override
        public void onSystemRemovedFromStore(Store<ChunkStore> store) {
            service.unloadWorld(store.getExternalData().getWorld());
        }
    }
}
