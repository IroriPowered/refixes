package cc.irori.refixes.early.util;

import java.util.HashMap;
import java.util.Map;

public final class PublishedCounters {

    private static final Map<String, Long> published = new HashMap<>();

    private PublishedCounters() {}

    public static synchronized long delta(String name, long total) {
        Long last = published.get(name);
        long delta = total - (last == null ? 0L : last);
        if (delta <= 0L) {
            return 0L;
        }
        published.put(name, total);
        return delta;
    }
}
