package cc.irori.refixes.service;

public final class OwnedKeepLoadedRegression {
    private static final class Counter {
        int acquired;
        int released;
    }

    public static void main(String[] args) {
        Counter first = new Counter();
        Counter second = new Counter();
        OwnedKeepLoaded<Counter> owned = new OwnedKeepLoaded<>(c -> c.acquired++, c -> c.released++);
        owned.set(first);
        owned.set(first);
        require(first.acquired == 1 && first.released == 0, "same component acquired twice");
        owned.set(second);
        require(first.acquired == 1 && first.released == 1, "replacement leaked old component");
        require(second.acquired == 1 && second.released == 0, "replacement not retained");
        owned.clear();
        owned.clear();
        require(second.released == 1, "release was not idempotent");
        owned.set(first);
        owned.clear();
        require(first.acquired == 2 && first.released == 2, "reacquisition unbalanced");
        System.out.println("OwnedKeepLoadedRegression passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
