package cc.irori.refixes.early.util;

import java.util.IdentityHashMap;
import java.util.concurrent.locks.StampedLock;

public final class SectionLockHold {
    private static final ThreadLocal<IdentityHashMap<StampedLock, Integer>> HELD =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private SectionLockHold() {}

    public static void acquire(StampedLock lock) {
        IdentityHashMap<StampedLock, Integer> map = HELD.get();
        Integer n = map.get(lock);
        map.put(lock, n == null ? 1 : n + 1);
    }

    public static void release(StampedLock lock) {
        IdentityHashMap<StampedLock, Integer> map = HELD.get();
        Integer n = map.get(lock);
        if (n == null || n <= 1) {
            map.remove(lock);
            if (map.isEmpty()) {
                HELD.remove();
            }
            return;
        }
        map.put(lock, n - 1);
    }

    public static boolean heldByCurrentThread(StampedLock lock) {
        Integer n = HELD.get().get(lock);
        return n != null && n > 0 && lock.isWriteLocked();
    }
}
