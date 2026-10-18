package cc.irori.refixes.early.util;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

public final class RepulsionPolicy {
    private static final int MAX_RETAINED_CAPACITY = 256;
    private static final ThreadLocal<ReferenceArrayList<Object>> AVAILABLE = new ThreadLocal<>();
    private static final ThreadLocal<ReferenceArrayList<Object>> ACTIVE = new ThreadLocal<>();

    private RepulsionPolicy() {}

    public static ReferenceArrayList<Object> borrow() {
        ReferenceArrayList<Object> list = AVAILABLE.get();
        AVAILABLE.remove();
        if (list == null) return new ReferenceArrayList<>();
        list.clear();
        return list;
    }

    public static void activate(ReferenceArrayList<Object> list) {
        ACTIVE.set(list);
    }

    public static ReferenceArrayList<Object> beginTick() {
        ReferenceArrayList<Object> previous = ACTIVE.get();
        ACTIVE.remove();
        return previous;
    }

    public static void endTick(ReferenceArrayList<Object> previous) {
        ReferenceArrayList<Object> active = ACTIVE.get();
        ACTIVE.remove();
        if (active != null) release(active);
        if (previous != null) ACTIVE.set(previous);
    }

    public static void release(ReferenceArrayList<Object> list) {
        list.clear();
        if (list.elements().length <= MAX_RETAINED_CAPACITY && AVAILABLE.get() == null) {
            AVAILABLE.set(list);
        }
    }
}
