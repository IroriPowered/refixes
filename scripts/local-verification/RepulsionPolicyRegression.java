package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.RepulsionPolicy;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

public final class RepulsionPolicyRegression {
    public static void main(String[] args) {
        sequentialReuse();
        nestedBorrow();
        oversizedListIsDropped();
        System.out.println("RepulsionPolicyRegression PASS reentrant bounded cleared reuse and ordered entries");
    }

    private static void sequentialReuse() {
        ReferenceArrayList<Object> first = RepulsionPolicy.borrow();
        Object a = new Object();
        Object b = new Object();
        first.add(a);
        first.add(b);
        RepulsionPolicy.activate(first);
        RepulsionPolicy.endTick(null);
        ReferenceArrayList<Object> second = RepulsionPolicy.borrow();
        check(second == first, "released list was not reused");
        check(second.isEmpty(), "reused list retained elements");
        second.add(a);
        second.add(b);
        check(second.get(0) == a && second.get(1) == b, "list order changed");
        RepulsionPolicy.activate(second);
        RepulsionPolicy.endTick(null);
    }

    private static void nestedBorrow() {
        ReferenceArrayList<Object> outer = RepulsionPolicy.borrow();
        RepulsionPolicy.activate(outer);
        ReferenceArrayList<Object> previous = RepulsionPolicy.beginTick();
        ReferenceArrayList<Object> inner = RepulsionPolicy.borrow();
        check(inner != outer, "nested tick borrowed active list");
        RepulsionPolicy.activate(inner);
        RepulsionPolicy.endTick(previous);
        check(RepulsionPolicy.beginTick() == outer, "nested tick did not restore outer list");
        RepulsionPolicy.activate(outer);
        RepulsionPolicy.endTick(null);
    }

    private static void oversizedListIsDropped() {
        ReferenceArrayList<Object> oversized = RepulsionPolicy.borrow();
        for (int i = 0; i < 512; i++) oversized.add(new Object());
        check(oversized.elements().length > 256, "fixture did not exceed retention bound");
        RepulsionPolicy.release(oversized);
        ReferenceArrayList<Object> next = RepulsionPolicy.borrow();
        check(next != oversized, "oversized backing array was retained");
        RepulsionPolicy.release(next);
    }

    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
