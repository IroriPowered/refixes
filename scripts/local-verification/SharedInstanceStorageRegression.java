package local.hyinit.verify;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.joml.Vector3d;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class SharedInstanceStorageRegression {
    private SharedInstanceStorageRegression() {}

    public static void assertSaveSectionCallbacks() throws Exception {
        RecordingSaver backend = new RecordingSaver();
        Class<?> type = Class.forName("cc.irori.refixes.service.SharedInstanceStorage");
        Constructor<?> ctor = type.getDeclaredConstructor(IChunkSaver.Cubic.class, Store.class);
        ctor.setAccessible(true);
        Object storage = ctor.newInstance(backend, null);
        Method saveSection = type.getMethod(
                "saveSection",
                int.class,
                int.class,
                int.class,
                Store.class,
                Ref.class,
                Executor.class,
                Runnable.class);

        ConcurrentLinkedDeque<Runnable> queue = new ConcurrentLinkedDeque<>();
        Executor caller = queue::add;
        AtomicInteger callbacks = new AtomicInteger();
        Runnable onIo = callbacks::incrementAndGet;

        backend.throwNext = true;
        @SuppressWarnings("unchecked")
        CompletableFuture<Void> failed = (CompletableFuture<Void>) saveSection.invoke(
                storage, 0, 0, 0, null, null, caller, onIo);
        if (!failed.isCompletedExceptionally()) {
            throw new AssertionError("Backend throw must complete the wrapper future exceptionally");
        }
        if (callbacks.get() != 1) {
            throw new AssertionError("onIoComplete must run when the backend throws before returning: " + callbacks);
        }
        if (!queue.isEmpty()) {
            throw new AssertionError("Caller completionExecutor must stay undrained on backend throw");
        }
        if (backend.saveCalls.get() != 1) {
            throw new AssertionError("Backend throw path should invoke saveSection once");
        }

        int before = callbacks.get();
        @SuppressWarnings("unchecked")
        CompletableFuture<Void> first = (CompletableFuture<Void>) saveSection.invoke(
                storage, 0, 0, 0, null, null, caller, onIo);
        @SuppressWarnings("unchecked")
        CompletableFuture<Void> duplicate = (CompletableFuture<Void>) saveSection.invoke(
                storage, 0, 0, 0, null, null, caller, onIo);
        if (first.isDone() || duplicate.isDone()) {
            throw new AssertionError("Deduped saveSection futures must stay pending until the backend completes");
        }
        if (backend.saveCalls.get() != 2) {
            throw new AssertionError("Duplicate saveSection must not reissue the backend write: " + backend.saveCalls);
        }
        if (callbacks.get() != before) {
            throw new AssertionError("onIoComplete must not run before the backend future completes");
        }
        if (!queue.isEmpty()) {
            throw new AssertionError("Wrapper must not schedule onIoComplete on the caller completionExecutor");
        }

        backend.raw.get().complete(null);
        if (callbacks.get() != before + 2) {
            throw new AssertionError("First and duplicate onIoComplete must each fire once inline: " + callbacks);
        }
        if (!first.isDone() || first.isCompletedExceptionally()) {
            throw new AssertionError("Wrapper future must complete normally after backend success");
        }
        if (!queue.isEmpty()) {
            throw new AssertionError("Caller completionExecutor queue must still be undrained");
        }
        System.out.println("[LocalVerify] SharedInstanceStorage saveSection callback regression PASS");
    }
    public static void runWithRegistry(Store<ChunkStore> chunkStore) throws Exception {
        if (chunkStore == null) {
            throw new AssertionError("Booted ChunkStore is required for UUID-less holder preservation");
        }
        Store<EntityStore> owning = EntityStore.REGISTRY.addStore(
                new EntityStore(chunkStore.getExternalData().getWorld()), EmptyResourceStorage.get());
        try {
            runOnStoreThread(chunkStore);
        } finally {
            EntityStore.REGISTRY.removeStore(owning);
        }
    }

    static void runOnStoreThread(Store<ChunkStore> chunkStore) throws Exception {
        RecordingSaver backend = new RecordingSaver();
        Class<?> type = Class.forName("cc.irori.refixes.service.SharedInstanceStorage");
        Constructor<?> ctor = type.getDeclaredConstructor(IChunkSaver.Cubic.class, Store.class);
        ctor.setAccessible(true);
        Object storage = ctor.newInstance(backend, chunkStore);
        Method saveEntitySection = type.getMethod(
                "saveEntitySection",
                int.class,
                int.class,
                int.class,
                Store.class,
                EntitySection.class,
                Executor.class,
                Runnable.class);

        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        Vector3d expectedPosition = new Vector3d(8.5, 16.25, 24.75);
        TransformComponent transform = new TransformComponent();
        transform.getPosition().set(expectedPosition);
        holder.putComponent(TransformComponent.getComponentType(), transform);
        if (holder.getComponent(UUIDComponent.getComponentType()) != null) {
            throw new AssertionError("UUID-less fixture holder must omit UUIDComponent");
        }
        EntitySection section = new EntitySection();
        section.loadEntityHolder(holder);

        @SuppressWarnings("unchecked")
        CompletableFuture<Void> first = (CompletableFuture<Void>) saveEntitySection.invoke(
                storage, 3, 4, 5, chunkStore, section, null, null);
        if (first.isCompletedExceptionally()) {
            try {
                first.join();
            } catch (Throwable error) {
                throw new AssertionError("saveEntitySection failed before dispatching a snapshot", error);
            }
        }
        if (backend.entitySaveCalls.get() != 1) {
            throw new AssertionError("Wrapper never called the backend saveEntitySection: "
                    + backend.entitySaveCalls.get());
        }
        Snapshot firstSnap = snapshot(backend.lastEntitySection);
        if (!firstSnap.position.equals(expectedPosition, 1.0e-6)) {
            throw new AssertionError("First save changed the original transform: " + firstSnap.position);
        }
        backend.entityRaw.get().completeExceptionally(new RuntimeException("first-fail"));
        try {
            first.join();
            throw new AssertionError("First UUID-less save must complete exceptionally");
        } catch (Exception ignored) {
            if (!first.isCompletedExceptionally()) {
                throw new AssertionError("First UUID-less save must complete exceptionally");
            }
        }

        @SuppressWarnings("unchecked")
        CompletableFuture<Void> retry = (CompletableFuture<Void>) saveEntitySection.invoke(
                storage, 3, 4, 5, chunkStore, section, null, null);
        if (backend.entitySaveCalls.get() != 2) {
            throw new AssertionError("Retry did not dispatch a second backend entity save: "
                    + backend.entitySaveCalls.get());
        }
        CompletableFuture<Void> retryIo = backend.entityRaw.get();
        if (retry.isDone() || retryIo == null || retryIo.isDone()) {
            throw new AssertionError("Retry must wait for the second backend write to complete");
        }
        Snapshot retrySnap = snapshot(backend.lastEntitySection);
        if (!firstSnap.uuid.equals(retrySnap.uuid)) {
            throw new AssertionError("Retry UUID must stay stable: " + firstSnap.uuid + " vs " + retrySnap.uuid);
        }
        if (!retrySnap.position.equals(expectedPosition, 1.0e-6)) {
            throw new AssertionError("Retry changed the original transform: " + retrySnap.position);
        }
        if (!retryIo.complete(null)) {
            throw new AssertionError("The retry backend write was already settled");
        }
        retry.join();
        if (retry.isCompletedExceptionally()) {
            throw new AssertionError("Retry UUID-less save must complete normally");
        }
        System.out.println("[LocalVerify] SharedInstanceStorage UUID-less holder regression PASS " + firstSnap.uuid);
    }

    private static Snapshot snapshot(EntitySection section) {
        if (section == null) {
            throw new AssertionError("Backend received no EntitySection snapshot");
        }
        if (section.getEntityHolders() == null || section.getEntityHolders().isEmpty()) {
            throw new AssertionError("Backend received an empty EntitySection snapshot: "
                    + "the UUID-less serializable entity was dropped");
        }
        Holder<EntityStore> holder = section.getEntityHolders().get(0);
        UUIDComponent uuid = holder.getComponent(UUIDComponent.getComponentType());
        TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
        if (uuid == null || uuid.getUuid() == null || transform == null) {
            throw new AssertionError("Snapshot must include assigned UUID and original TransformComponent");
        }
        return new Snapshot(uuid.getUuid(), new Vector3d(transform.getPosition()));
    }

    private record Snapshot(UUID uuid, Vector3d position) {}


    private static final class RecordingSaver implements IChunkSaver.Cubic {
        final AtomicInteger saveCalls = new AtomicInteger();
        final AtomicReference<CompletableFuture<Void>> raw = new AtomicReference<>();
        final AtomicInteger entitySaveCalls = new AtomicInteger();
        final AtomicReference<CompletableFuture<Void>> entityRaw = new AtomicReference<>();
        volatile EntitySection lastEntitySection;
        volatile boolean throwNext;

        @Override
        public CompletableFuture<Void> saveSection(
                int x, int y, int z, Store<ChunkStore> store, Ref<ChunkStore> sectionRef,
                Executor completionExecutor, Runnable onIoComplete) {
            saveCalls.incrementAndGet();
            if (throwNext) {
                throwNext = false;
                throw new RuntimeException("backend-fail");
            }
            CompletableFuture<Void> future = new CompletableFuture<>();
            raw.set(future);
            if (completionExecutor != null) {
                return future.whenCompleteAsync((ignored, error) -> {}, completionExecutor);
            }
            return future.whenComplete((ignored, error) -> {});
        }

        @Override
        public CompletableFuture<Void> saveEntitySection(
                int x, int y, int z, Store<ChunkStore> store, EntitySection section,
                Executor completionExecutor, Runnable onIoComplete) {
            entitySaveCalls.incrementAndGet();
            lastEntitySection = section;
            CompletableFuture<Void> future = new CompletableFuture<>();
            entityRaw.set(future);
            if (completionExecutor != null) {
                return future.whenCompleteAsync((ignored, error) -> {}, completionExecutor);
            }
            return future;
        }

        @Override
        public CompletableFuture<Void> saveBlockComponentSection(
                int x, int y, int z, Store<ChunkStore> store, BlockComponentSection section,
                Executor completionExecutor, Runnable onIoComplete) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> saveEntity(UUID uuid, Store<EntityStore> store, Ref<EntityStore> entityRef) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> removeEntity(UUID uuid) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> saveHolder(int x, int z, Holder<ChunkStore> holder) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> removeHolder(int x, int z) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public LongList getIndexes() {
            return new LongArrayList();
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
    }
}
