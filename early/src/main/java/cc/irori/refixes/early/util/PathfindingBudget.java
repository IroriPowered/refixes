package cc.irori.refixes.early.util;

public final class PathfindingBudget {

    private static final ThreadLocal<int[]> REMAINING = ThreadLocal.withInitial(() -> new int[] {0});

    private PathfindingBudget() {}

    public static void reset(int max) {
        REMAINING.get()[0] = max;
    }

    public static boolean tryConsume() {
        int[] cell = REMAINING.get();
        if (cell[0] <= 0) {
            return false;
        }
        cell[0]--;
        return true;
    }
}
