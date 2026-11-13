package local.hyinit.verify;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.builtin.mounts.MountPlugin;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.interaction.blocktrack.BlockCounter;
import com.hypixel.hytale.server.core.modules.interaction.blocktrack.TrackedPlacement;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.systems.NPCDeathSystems;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class EntityGuardRegression {
    private static final String SECOND_WORLD = "refixes-entity-guard";

    private EntityGuardRegression() {}

    public static Map<String, String> run(World first) throws Exception {
        World second = Universe.get().addWorld(SECOND_WORLD, "Void", "Empty").get(60, TimeUnit.SECONDS);
        Map<String, String> results = new LinkedHashMap<>();
        try {
            Ref<EntityStore> foreign = onThread(second, () -> second.getEntityStore().getStore()
                    .addEntity(Archetype.empty(), AddReason.SPAWN));
            results.put("mount", onThread(first, () -> mount(first, foreign)));
            results.put("npcKills", onThread(first, () -> npcKills(first, foreign)));
            results.put("trackedPlacement", onThread(second, () -> trackedPlacement(second)));
            onThread(second, () -> second.getEntityStore().getStore().removeEntity(foreign, RemoveReason.REMOVE));
        } finally {
            Universe.get().removeWorld(SECOND_WORLD);
        }
        System.out.println("[LocalVerify] Entity guard regressions PASS " + results);
        return results;
    }

    private static String mount(World world, Ref<EntityStore> foreign) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> local = store.addEntity(Archetype.empty(), AddReason.SPAWN);
        MountPlugin.resetOriginalPlayerMovementSettings(local, store, -1);
        Throwable foreignFailure = failure(() -> MountPlugin.resetOriginalPlayerMovementSettings(foreign, store, -1));
        store.removeEntity(local, RemoveReason.REMOVE);
        Throwable removedFailure = failure(() -> MountPlugin.resetOriginalPlayerMovementSettings(local, store, -1));
        return expectGuard("MixinMountPlugin", foreignFailure, "Incorrect store for entity reference") + "; removed "
                + expectGuard("MixinMountPlugin", removedFailure, "Invalid entity reference!");
    }

    private static String npcKills(World world, Ref<EntityStore> foreign) throws Exception {
        Store<EntityStore> store = world.getEntityStore().getStore();
        Ref<EntityStore> victim = store.addEntity(Archetype.empty(), AddReason.SPAWN);
        try {
            Constructor<DeathComponent> constructor = DeathComponent.class.getDeclaredConstructor(Damage.class);
            constructor.setAccessible(true);
            DeathComponent death = constructor.newInstance(new Damage(new Damage.EntitySource(foreign), 0, 1.0F));
            NPCDeathSystems.NPCKillsEntitySystem system = new NPCDeathSystems.NPCKillsEntitySystem();
            Throwable failure = failure(() -> system.onComponentAdded(victim, death, store, null));
            return expectGuard("MixinNPCKillsEntitySystem", failure, "Incorrect store for entity reference");
        } finally {
            store.removeEntity(victim, RemoveReason.REMOVE);
        }
    }

    @SuppressWarnings("unchecked")
    private static String trackedPlacement(World world) throws Exception {
        Store<ChunkStore> store = world.getChunkStore().getStore();
        BlockCounter counter = store.getResource(BlockCounter.getResourceType());
        Field field = BlockCounter.class.getDeclaredField("blockPlacementCounts");
        field.setAccessible(true);
        Object2IntMap<String> counts = (Object2IntMap<String>) field.get(counter);
        counter.trackBlock("RefixesProbe");
        removeTracked(store, new TrackedPlacement("RefixesProbe"));
        check(counter.getBlockPlacementCount("RefixesProbe") == 0, "named placement removal must still decrement");
        removeTracked(store, new TrackedPlacement());
        boolean nullKey = counts.containsKey(null);
        Throwable encode = failure(() -> BlockCounter.CODEC.encode(counter, ExtraInfo.THREAD_LOCAL.get()));
        counts.removeInt(null);
        counts.removeInt("RefixesProbe");
        if (EarlyOptions.isMixinEnabled("MixinTrackedPlacementOnAddRemove")) {
            check(!nullKey && encode == null, "guarded unnamed removal corrupted BlockCounter: " + encode);
            return "guarded: no null key, BlockCounter encodes, named decrement preserved";
        }
        check(nullKey && encode != null, "native unnamed removal no longer corrupts BlockCounter");
        return "native: null key inserted, encode threw " + encode.getClass().getSimpleName();
    }

    private static void removeTracked(Store<ChunkStore> store, TrackedPlacement placement) {
        Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
        holder.addComponent(TrackedPlacement.getComponentType(), placement);
        Ref<ChunkStore> ref = store.addEntity(holder, AddReason.LOAD);
        store.removeEntity(ref, RemoveReason.REMOVE);
    }

    private static String expectGuard(String mixin, Throwable failure, String nativeMessage) {
        if (EarlyOptions.isMixinEnabled(mixin)) {
            check(failure == null, mixin + " did not guard foreign-store reference: " + failure);
            return "guarded: foreign-store reference skipped";
        }
        check(failure instanceof IllegalStateException && nativeMessage.equals(failure.getMessage()),
                "native foreign-store behaviour changed: " + failure);
        return "native: " + failure.getMessage();
    }

    private static Throwable failure(ThrowingRunnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static <T> T onThread(World world, Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        world.execute(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
