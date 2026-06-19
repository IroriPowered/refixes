package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Store;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

public final class PathfindingBudget {

    private static final class Cell {
        final AtomicInteger searches = new AtomicInteger();
        final AtomicInteger nodes = new AtomicInteger();
    }

    private static final Map<Store<?>, Cell> CELLS = new ConcurrentHashMap<>();
    private static final LongAdder DEFERRALS = new LongAdder();

    private PathfindingBudget() {}

    public static long deferrals() {
        return DEFERRALS.sum();
    }

    public static void reset(Store<?> store, int maxSearches, int maxNodes) {
        if (store == null) {
            return;
        }
        Cell cell = CELLS.computeIfAbsent(store, k -> new Cell());
        cell.searches.set(maxSearches);
        cell.nodes.set(maxNodes <= 0 ? Integer.MAX_VALUE : maxNodes);
    }

    public static void remove(Store<?> store) {
        if (store != null) {
            CELLS.remove(store);
        }
    }

    public static boolean tryConsume(ComponentAccessor<?> accessor) {
        Cell cell = cellOf(accessor);
        if (cell == null) {
            return true;
        }
        if (cell.searches.getAndDecrement() > 0) {
            return true;
        }
        DEFERRALS.increment();
        return false;
    }

    public static boolean tryConsumeNodes(ComponentAccessor<?> accessor, int cost) {
        Cell cell = cellOf(accessor);
        if (cell == null) {
            return true;
        }
        return cell.nodes.getAndAdd(-cost) > 0;
    }

    private static Cell cellOf(ComponentAccessor<?> accessor) {
        if (!(accessor instanceof CommandBuffer)) {
            return null;
        }
        Store<?> store = ((CommandBuffer<?>) accessor).getStore();
        return store == null ? null : CELLS.get(store);
    }
}
