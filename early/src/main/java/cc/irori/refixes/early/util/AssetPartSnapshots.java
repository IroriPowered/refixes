package cc.irori.refixes.early.util;

import cc.irori.refixes.early.accessor.CachedPacketAccess;
import com.hypixel.hytale.protocol.CachedPacket;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.io.VarInt;
import com.hypixel.hytale.protocol.packets.setup.AssetPart;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

public final class AssetPartSnapshots {

    private static final int SAMPLE = 1024;
    private static final ReferenceQueue<CachedPacket<?>> STALE = new ReferenceQueue<>();
    private static final ConcurrentHashMap<Key, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private AssetPartSnapshots() {}

    public static CachedPacket<?> cache(ToClientPacket packet, Operation<CachedPacket<?>> original) {
        if (packet.getClass() != AssetPart.class) return original.call(packet);
        if (PatchCounters.ENABLED) PatchCounters.ASSET_PART_SNAPSHOT_SHARING.countReached();
        byte[] part = ((AssetPart) packet).part;
        if (part == null) return original.call(packet);
        expunge();
        Key key = new Key(part.length, sample(part));
        Snapshot existing = SNAPSHOTS.get(key);
        CachedPacket<?> shared = existing == null ? null : existing.get();
        if (shared != null && matches(shared, part)) {
            if (PatchCounters.ENABLED) PatchCounters.ASSET_PART_SNAPSHOT_SHARING.countEffect();
            return shared;
        }
        CachedPacket<?> created = original.call(packet);
        SNAPSHOTS.put(key, new Snapshot(created, key));
        return created;
    }

    private static boolean matches(CachedPacket<?> cached, byte[] part) {
        if (cached.getPacketType() != AssetPart.class) return false;
        ByteBuf bytes = ((CachedPacketAccess) (Object) cached).refixes$getCachedBytes();
        if (bytes.refCnt() <= 0 || !bytes.hasArray()) return false;
        int header = 1 + VarInt.size(part.length);
        int readable = bytes.readableBytes();
        if (readable != header + part.length) return false;
        byte[] array = bytes.array();
        int start = bytes.arrayOffset() + bytes.readerIndex();
        if (array[start] != 1) return false;
        return Arrays.equals(array, start + header, start + readable, part, 0, part.length);
    }

    private static int sample(byte[] part) {
        int length = part.length;
        if (length <= 3 * SAMPLE) return Arrays.hashCode(part);
        int hash = hash(part, 0, 1);
        hash = hash(part, (length - SAMPLE) >>> 1, hash);
        return hash(part, length - SAMPLE, hash);
    }

    private static int hash(byte[] part, int from, int hash) {
        for (int i = from, end = from + SAMPLE; i < end; i++) hash = 31 * hash + part[i];
        return hash;
    }

    private static void expunge() {
        Reference<? extends CachedPacket<?>> stale;
        while ((stale = STALE.poll()) != null) {
            Snapshot snapshot = (Snapshot) stale;
            SNAPSHOTS.remove(snapshot.key, snapshot);
        }
    }

    private record Key(int length, int hash) {}

    private static final class Snapshot extends WeakReference<CachedPacket<?>> {

        private final Key key;

        Snapshot(CachedPacket<?> packet, Key key) {
            super(packet, STALE);
            this.key = key;
        }
    }
}
