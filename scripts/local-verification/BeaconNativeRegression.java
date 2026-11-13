package com.hypixel.hytale.component;

import com.hypixel.hytale.server.core.entity.reference.InvalidatablePersistentRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.SpawnBeaconReference;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.SpawnReferenceSystems;
import com.hypixel.hytale.server.spawning.beacons.LegacySpawnBeaconEntity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;

public final class BeaconNativeRegression {
    private BeaconNativeRegression() {}

    public static void main(String[] args) throws Exception {
        assertNativeNullController();
    }

    @SuppressWarnings("unchecked")
    public static void assertNativeNullController() throws Exception {
        FixtureStore store = allocate(FixtureStore.class);
        store.components = new IdentityHashMap<>();
        Ref<EntityStore> npcRef = new Ref<>(store, 0);
        Ref<EntityStore> beaconRef = new Ref<>(store, 1);
        ComponentType<EntityStore, SpawnBeaconReference> spawnType = allocate(ComponentType.class);
        ComponentType<EntityStore, LegacySpawnBeaconEntity> beaconType = allocate(ComponentType.class);
        ComponentType<EntityStore, NPCEntity> npcType = allocate(ComponentType.class);
        SpawnBeaconReference spawn = new SpawnBeaconReference();
        FixtureReference reference = new FixtureReference();
        reference.entity = beaconRef;
        reference.store = store;
        set(spawn, "reference", reference);
        NPCEntity npc = allocate(NPCEntity.class);
        LegacySpawnBeaconEntity beacon = allocate(LegacySpawnBeaconEntity.class);
        if (beacon.getSpawnController() != null || npc.isDespawning()) {
            throw new AssertionError("Invalid native null-controller fixture");
        }
        store.components.put(spawnType, spawn);
        store.components.put(beaconType, beacon);
        store.components.put(npcType, npc);
        SpawnReferenceSystems.BeaconAddRemoveSystem system = allocate(SpawnReferenceSystems.BeaconAddRemoveSystem.class);
        set(system, "spawnReferenceComponentType", spawnType);
        set(system, "legacySpawnBeaconComponent", beaconType);
        set(system, "npcComponentType", npcType);
        system.onEntityAdded(npcRef, AddReason.LOAD, store, null);
        if (!npc.isDespawning() || reference.assignments != 1 || store.reads != 3) {
            throw new AssertionError("Native null-controller load must despawn after resolving the beacon");
        }
        System.out.println("[LocalVerify] Native beacon null-controller despawn regression PASS");
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafeType = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeType.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        Method allocate = unsafeType.getMethod("allocateInstance", Class.class);
        return type.cast(allocate.invoke(singleton.get(null), type));
    }

    private static void set(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class FixtureStore extends Store<EntityStore> {
        private Map<ComponentType<?, ?>, Component<?>> components;
        private int reads;

        private FixtureStore() {
            super(null, 0, null, null);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Component<EntityStore>> T getComponent(
                Ref<EntityStore> ref, ComponentType<EntityStore, T> type) {
            if (ref == null || ref.getStore() != this || !components.containsKey(type)) {
                throw new AssertionError("Unexpected component lookup in beacon regression");
            }
            reads++;
            return (T) components.get(type);
        }
    }

    private static final class FixtureReference extends InvalidatablePersistentRef {
        private Ref<EntityStore> entity;
        private Store<EntityStore> store;
        private int assignments;

        @Override
        public Ref<EntityStore> getEntity(ComponentAccessor<EntityStore> accessor) {
            if (accessor != store) throw new AssertionError("Unexpected beacon reference accessor");
            return entity;
        }

        @Override
        public void setEntity(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
            if (ref != entity || accessor != store) throw new AssertionError("Unexpected beacon reference assignment");
            assignments++;
        }
    }
}
