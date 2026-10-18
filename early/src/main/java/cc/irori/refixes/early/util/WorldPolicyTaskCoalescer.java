package cc.irori.refixes.early.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public final class WorldPolicyTaskCoalescer {
    private static final ReferenceQueue<Object> STALE = new ReferenceQueue<>();
    private static final Map<WorldKey, Object> PENDING = new HashMap<>();
    private static final Object CLOSED = new Object();

    private WorldPolicyTaskCoalescer() {}

    public static void close(Object key) {
        synchronized (PENDING) {
            expunge();
            PENDING.put(new WorldKey(key, STALE), CLOSED);
        }
    }

    public static boolean submit(Object key, Executor owner, Runnable accepted) {
        WorldKey worldKey = new WorldKey(key, STALE);
        Object generation = new Object();
        synchronized (PENDING) {
            expunge();
            if (PENDING.putIfAbsent(worldKey, generation) != null) return false;
        }
        try {
            owner.execute(() -> {
                boolean current;
                synchronized (PENDING) {
                    current = PENDING.get(worldKey) == generation;
                    if (current) PENDING.remove(worldKey);
                }
                if (current) accepted.run();
            });
            return true;
        } catch (RuntimeException | Error failure) {
            synchronized (PENDING) {
                if (PENDING.get(worldKey) == generation) PENDING.remove(worldKey);
            }
            throw failure;
        }
    }

    private static void expunge() {
        WorldKey stale;
        while ((stale = (WorldKey) STALE.poll()) != null) PENDING.remove(stale);
    }

    private static final class WorldKey extends WeakReference<Object> {
        private final int hash;

        WorldKey(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.hash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return this.hash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            Object referent = this.get();
            return other instanceof WorldKey key && referent != null && referent == key.get();
        }
    }
}
