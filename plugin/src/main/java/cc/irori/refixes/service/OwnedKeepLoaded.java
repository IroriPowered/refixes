package cc.irori.refixes.service;

import java.util.function.Consumer;

final class OwnedKeepLoaded<T> {
    private final Consumer<T> acquire;
    private final Consumer<T> release;
    private T value;

    OwnedKeepLoaded(Consumer<T> acquire, Consumer<T> release) {
        this.acquire = acquire;
        this.release = release;
    }

    T get() {
        return value;
    }

    void set(T replacement) {
        if (value == replacement) return;
        clear();
        if (replacement != null) {
            acquire.accept(replacement);
            value = replacement;
        }
    }

    void clear() {
        T previous = value;
        value = null;
        if (previous != null) release.accept(previous);
    }
}
