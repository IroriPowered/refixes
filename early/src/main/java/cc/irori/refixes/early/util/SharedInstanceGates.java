package cc.irori.refixes.early.util;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SharedInstanceGates {

    private static final Map<String, CompletableFuture<Void>> GATES = new ConcurrentHashMap<>();

    private SharedInstanceGates() {}

    public static boolean claim(String name, CompletableFuture<Void> gate) {
        return GATES.putIfAbsent(key(name), gate) == null;
    }

    public static CompletableFuture<Void> gate(String name) {
        return name == null ? null : GATES.get(key(name));
    }

    public static void complete(String name, CompletableFuture<Void> gate) {
        if (gate == null) return;
        GATES.remove(key(name), gate);
        gate.complete(null);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
