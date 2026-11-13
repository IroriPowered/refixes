package cc.irori.refixes.early.util;

import java.lang.ref.ReferenceQueue;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public final class WorldPolicyTaskCoalescer {
    private static final ReferenceQueue<Object> STALE = new ReferenceQueue<>();
    private static final Map<WorldPolicyKey, Object> PENDING = new HashMap<>();
    private static final Object CLOSED = new Object();

    private WorldPolicyTaskCoalescer() {}

    public static void close(Object key) {
        synchronized (PENDING) {
            expunge();
            PENDING.put(new WorldPolicyKey(key, STALE), CLOSED);
        }
    }

    public static boolean submit(Object key, Executor owner, Runnable accepted) {
        WorldPolicyKey worldKey = new WorldPolicyKey(key, STALE);
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
        WorldPolicyKey stale;
        while ((stale = (WorldPolicyKey) STALE.poll()) != null) PENDING.remove(stale);
    }
}
