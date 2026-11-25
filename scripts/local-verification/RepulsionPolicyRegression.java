package cc.irori.refixes.early.mixin;

import static local.hyinit.verify.RegressionRunner.require;

import cc.irori.refixes.early.util.RepulsionPolicy;

import it.unimi.dsi.fastutil.objects.ReferenceArrayList;

public final class RepulsionPolicyRegression {
    public static void main(String[] args) {
        sequentialReuse();
        nestedBorrow();
        System.out.println("RepulsionPolicyRegression PASS nested borrow and cleared reuse");
    }

    private static void sequentialReuse() {
        ReferenceArrayList<Object> first = RepulsionPolicy.borrow();
        first.add(new Object());
        RepulsionPolicy.activate(first);
        RepulsionPolicy.endTick(null);
        ReferenceArrayList<Object> second = RepulsionPolicy.borrow();
        require(second == first, "released list was not reused");
        require(second.isEmpty(), "reused list retained elements");
        RepulsionPolicy.activate(second);
        RepulsionPolicy.endTick(null);
    }

    private static void nestedBorrow() {
        ReferenceArrayList<Object> outer = RepulsionPolicy.borrow();
        RepulsionPolicy.activate(outer);
        ReferenceArrayList<Object> previous = RepulsionPolicy.beginTick();
        ReferenceArrayList<Object> inner = RepulsionPolicy.borrow();
        require(inner != outer, "nested tick borrowed active list");
        RepulsionPolicy.activate(inner);
        RepulsionPolicy.endTick(previous);
        require(RepulsionPolicy.beginTick() == outer, "nested tick did not restore outer list");
        RepulsionPolicy.activate(outer);
        RepulsionPolicy.endTick(null);
    }
}
