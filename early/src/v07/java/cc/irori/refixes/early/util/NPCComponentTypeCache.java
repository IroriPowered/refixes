package cc.irori.refixes.early.util;

import cc.irori.hyinit.api.IfHytale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@IfHytale("<0.8.0-pre.1")
public final class NPCComponentTypeCache {
    private final AtomicInteger writers = new AtomicInteger();
    private final AtomicInteger generation = new AtomicInteger();
    private volatile Entry entry;

    public void beginWrite() {
        writers.incrementAndGet();
        generation.incrementAndGet();
    }

    public void endWrite() {
        writers.decrementAndGet();
    }

    public Object get(Map<?, ?> map, Object key) {
        if (PatchCounters.ENABLED) PatchCounters.NPC_COMPONENT_TYPE.countReached();
        int current = generation.get();
        if (writers.get() != 0) return map.get(key);
        Entry cached = entry;
        if (cached != null && cached.generation() == current) {
            if (PatchCounters.ENABLED) PatchCounters.NPC_COMPONENT_TYPE.countEffect();
            return cached.value();
        }
        Object value = map.get(key);
        entry = new Entry(current, value);
        return value;
    }

    @IfHytale("<0.8.0-pre.1")
    private record Entry(int generation, Object value) {}
}
