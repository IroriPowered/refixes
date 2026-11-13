package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.MapMarkerLockOwner;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.function.consumer.TriIntObjectConsumer;
import com.hypixel.hytale.math.data.Int3ObjectOpenHashMap;
import com.hypixel.hytale.protocol.packets.worldmap.MapMarkerIconSize;
import com.hypixel.hytale.server.core.universe.world.meta.state.BlockMapMarker;
import com.hypixel.hytale.server.core.universe.world.meta.state.BlockMapMarkersResource;
import com.hypixel.hytale.server.core.universe.world.meta.state.BlockMapMarkersResource.BlockMapMarkerData;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.MapMarkerAlert;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.joml.Vector3i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockMapMarkersResource.class)
public abstract class MixinMapMarkerLocking implements MapMarkerLockOwner {

    @Unique
    private static final String FOR_EACH =
            "Lcom/hypixel/hytale/math/data/Int3ObjectOpenHashMap;forEach(Lcom/hypixel/hytale/function/consumer/TriIntObjectConsumer;)V";

    @Unique
    private static final String GET_MARKERS =
            "Lcom/hypixel/hytale/server/core/universe/world/meta/state/BlockMapMarkersResource;getMarkers()Lcom/hypixel/hytale/math/data/Int3ObjectOpenHashMap;";

    @Unique
    private static final String RETAIN =
            "retainRevealedMarkers(Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/server/core/entity/entities/player/data/PlayerWorldData;)I";

    @Unique
    private final ReentrantReadWriteLock refixes$lock = new ReentrantReadWriteLock();

    @Override
    public ReentrantReadWriteLock refixes$markerLock() {
        return refixes$lock;
    }

    @WrapMethod(
            method =
                    "getMarker(III)Lcom/hypixel/hytale/server/core/universe/world/meta/state/BlockMapMarkersResource$BlockMapMarkerData;",
            require = 1,
            allow = 1)
    private BlockMapMarkerData refixes$lockedGet(int x, int y, int z, Operation<BlockMapMarkerData> original) {
        Lock lock = refixes$lock.readLock();
        lock.lock();
        try {
            return original.call(x, y, z);
        } finally {
            lock.unlock();
        }
    }

    @WrapMethod(
            method =
                    "addMarker(Lorg/joml/Vector3i;Ljava/lang/String;Ljava/lang/String;ZLcom/hypixel/hytale/protocol/packets/worldmap/MapMarkerIconSize;Ljava/lang/String;Lcom/hypixel/hytale/server/core/universe/world/worldmap/markers/MapMarkerAlert;)Lcom/hypixel/hytale/server/core/universe/world/meta/state/BlockMapMarkersResource$BlockMapMarkerData;",
            require = 1,
            allow = 1)
    private BlockMapMarkerData refixes$lockedAdd(
            Vector3i position,
            String name,
            String icon,
            boolean discoverable,
            MapMarkerIconSize iconSize,
            String compass,
            MapMarkerAlert alert,
            Operation<BlockMapMarkerData> original) {
        Lock lock = refixes$lock.writeLock();
        lock.lock();
        try {
            return original.call(position, name, icon, discoverable, iconSize, compass, alert);
        } finally {
            lock.unlock();
        }
    }

    @WrapMethod(
            method =
                    "removeMarker(Lorg/joml/Vector3i;)Lcom/hypixel/hytale/server/core/universe/world/meta/state/BlockMapMarkersResource$BlockMapMarkerData;",
            require = 1,
            allow = 1)
    private BlockMapMarkerData refixes$lockedRemove(Vector3i position, Operation<BlockMapMarkerData> original) {
        Lock lock = refixes$lock.writeLock();
        lock.lock();
        try {
            return original.call(position);
        } finally {
            lock.unlock();
        }
    }

    @WrapMethod(method = "clone()Lcom/hypixel/hytale/component/Resource;", require = 1, allow = 1)
    private Resource<ChunkStore> refixes$lockedClone(Operation<Resource<ChunkStore>> original) {
        Lock lock = refixes$lock.readLock();
        lock.lock();
        try {
            return original.call();
        } finally {
            lock.unlock();
        }
    }

    @WrapMethod(
            method =
                    "lambda$static$3(Lcom/hypixel/hytale/server/core/universe/world/meta/state/BlockMapMarkersResource;)Lcom/hypixel/hytale/math/data/Int3ObjectOpenHashMap;",
            require = 1,
            allow = 1)
    private static Int3ObjectOpenHashMap<BlockMapMarkerData> refixes$encodeCopy(
            BlockMapMarkersResource resource, Operation<Int3ObjectOpenHashMap<BlockMapMarkerData>> original) {
        Lock lock = ((MapMarkerLockOwner) resource).refixes$markerLock().readLock();
        lock.lock();
        try {
            Int3ObjectOpenHashMap<BlockMapMarkerData> markers = original.call(resource);
            return markers == null ? null : new Int3ObjectOpenHashMap<>(markers);
        } finally {
            lock.unlock();
        }
    }

    @WrapMethod(
            method =
                    "lambda$static$2(Lcom/hypixel/hytale/server/core/universe/world/meta/state/BlockMapMarkersResource;Lcom/hypixel/hytale/math/data/Int3ObjectOpenHashMap;)V",
            require = 1,
            allow = 1)
    private static void refixes$lockedDecode(
            BlockMapMarkersResource resource,
            Int3ObjectOpenHashMap<BlockMapMarkerData> markers,
            Operation<Void> original) {
        Lock lock = ((MapMarkerLockOwner) resource).refixes$markerLock().writeLock();
        lock.lock();
        try {
            original.call(resource, markers);
        } finally {
            lock.unlock();
        }
    }

    @WrapMethod(
            method =
                    "lambda$static$0(Lcom/hypixel/hytale/server/core/universe/world/meta/state/BlockMapMarkersResource;Ljava/util/Map;)V",
            require = 1,
            allow = 1)
    private static void refixes$lockedLegacyDecode(
            BlockMapMarkersResource resource, Map<String, BlockMapMarkerData> markers, Operation<Void> original) {
        Lock lock = ((MapMarkerLockOwner) resource).refixes$markerLock().writeLock();
        lock.lock();
        try {
            original.call(resource, markers);
        } finally {
            lock.unlock();
        }
    }

    @Mixin(BlockMapMarker.MarkerProvider.class)
    public abstract static class Provider {
        @WrapOperation(
                method =
                        "update(Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/server/core/entity/entities/Player;Lcom/hypixel/hytale/server/core/universe/world/worldmap/markers/MarkersCollector;)V",
                at = @At(value = "INVOKE", target = FOR_EACH),
                require = 1,
                allow = 1)
        private void refixes$iterateLocked(
                Int3ObjectOpenHashMap<BlockMapMarkerData> markers,
                TriIntObjectConsumer<? super BlockMapMarkerData> consumer,
                Operation<Void> original,
                @Local BlockMapMarkersResource resource) {
            Lock lock = ((MapMarkerLockOwner) resource).refixes$markerLock().readLock();
            lock.lock();
            try {
                original.call(markers, consumer);
            } finally {
                lock.unlock();
            }
        }
    }

    @Mixin(BlockMapMarker.class)
    public abstract static class Reveal {
        @WrapOperation(method = RETAIN, at = @At(value = "INVOKE", target = GET_MARKERS), require = 1, allow = 1)
        private static Int3ObjectOpenHashMap<BlockMapMarkerData> refixes$captureResource(
                BlockMapMarkersResource resource,
                Operation<Int3ObjectOpenHashMap<BlockMapMarkerData>> original,
                @Share("resource") LocalRef<BlockMapMarkersResource> captured) {
            captured.set(resource);
            return original.call(resource);
        }

        @WrapOperation(method = RETAIN, at = @At(value = "INVOKE", target = FOR_EACH), require = 1, allow = 1)
        private static void refixes$iterateLocked(
                Int3ObjectOpenHashMap<BlockMapMarkerData> markers,
                TriIntObjectConsumer<? super BlockMapMarkerData> consumer,
                Operation<Void> original,
                @Share("resource") LocalRef<BlockMapMarkersResource> captured) {
            Lock lock =
                    ((MapMarkerLockOwner) captured.get()).refixes$markerLock().readLock();
            lock.lock();
            try {
                original.call(markers, consumer);
            } finally {
                lock.unlock();
            }
        }
    }
}
