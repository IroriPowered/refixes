package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class PathfindingBudget {

    private static final class Lane {
        private final Set<Ref<?>> served = new HashSet<>();
        private final List<Ref<?>> admitted = new ArrayList<>();
        private int remaining;
        private int reserved;
        private long deniedCost;
        private boolean charged;

        synchronized void reset(int budget) {
            if (deniedCost == 0) {
                served.clear();
            } else {
                served.addAll(admitted);
                served.removeIf(ref -> !ref.isValid());
            }
            admitted.clear();
            reserved = (int) Math.min(deniedCost, Math.max(budget, 0));
            deniedCost = 0;
            remaining = budget;
            charged = false;
        }

        synchronized boolean tryAcquire(Ref<?> requester, int cost) {
            boolean waiting = served.isEmpty() || !served.contains(requester);
            int reserve = waiting ? 0 : reserved;
            if (cost <= remaining - reserve || (!charged && reserve == 0 && remaining > 0)) {
                remaining = Math.max(0, remaining - cost);
                charged = true;
                if (waiting) {
                    admitted.add(requester);
                    reserved = Math.max(0, reserved - cost);
                }
                return true;
            }
            if (waiting) {
                deniedCost += cost;
            }
            return false;
        }

        synchronized void clear() {
            served.clear();
            admitted.clear();
        }
    }

    private static final class Cell {
        final Lane searches = new Lane();
        final Lane nodes = new Lane();
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
        cell.searches.reset(maxSearches);
        cell.nodes.reset(maxNodes <= 0 ? Integer.MAX_VALUE : maxNodes);
    }

    public static void remove(Store<?> store) {
        if (store != null) {
            Cell cell = CELLS.remove(store);
            if (cell != null) {
                cell.searches.clear();
                cell.nodes.clear();
            }
        }
    }

    public static boolean tryConsume(ComponentAccessor<?> accessor, Ref<?> requester) {
        return tryAcquire(accessor, requester, false, 1);
    }

    public static boolean tryConsumeNodes(ComponentAccessor<?> accessor, Ref<?> requester, int cost) {
        return tryAcquire(accessor, requester, true, cost);
    }

    private static boolean tryAcquire(ComponentAccessor<?> accessor, Ref<?> requester, boolean nodes, int cost) {
        Cell cell = cellOf(accessor);
        if (cell == null) {
            return true;
        }
        if ((nodes ? cell.nodes : cell.searches).tryAcquire(requester, cost)) {
            return true;
        }
        DEFERRALS.increment();
        return false;
    }

    private static Cell cellOf(ComponentAccessor<?> accessor) {
        Store<?> store;
        if (accessor instanceof Store<?> direct) {
            store = direct;
        } else if (accessor instanceof CommandBuffer<?> buffer) {
            store = buffer.getStore();
        } else {
            return null;
        }
        return store == null ? null : CELLS.get(store);
    }
}
