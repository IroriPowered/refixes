package cc.irori.refixes.early.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

final class WorldPolicyKey extends WeakReference<Object> {
    private final int hash;

    WorldPolicyKey(Object referent, ReferenceQueue<Object> queue) {
        super(referent, queue);
        this.hash = System.identityHashCode(referent);
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        Object referent = this.get();
        return other instanceof WorldPolicyKey key && referent != null && referent == key.get();
    }
}
