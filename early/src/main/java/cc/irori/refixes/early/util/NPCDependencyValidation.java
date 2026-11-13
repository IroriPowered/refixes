package cc.irori.refixes.early.util;

import com.hypixel.hytale.server.npc.asset.builder.Builder;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

public final class NPCDependencyValidation {
    private static final ThreadLocal<Set<String>> REFERENCES = new ThreadLocal<>();
    private static final Map<Builder<?>, Set<String>> RETRIES = new WeakHashMap<>();

    private NPCDependencyValidation() {}

    public static boolean validate(Builder<?> builder, BooleanSupplier validation) {
        Set<String> previous = REFERENCES.get();
        Set<String> references = new HashSet<>();
        REFERENCES.set(references);
        boolean valid = false;
        try {
            valid = validation.getAsBoolean();
            return valid;
        } finally {
            if (previous == null) REFERENCES.remove();
            else REFERENCES.set(previous);
            synchronized (RETRIES) {
                if (valid || references.isEmpty()) RETRIES.remove(builder);
                else RETRIES.put(builder, Set.copyOf(references));
            }
        }
    }

    public static void reference(String name) {
        Set<String> references = REFERENCES.get();
        if (references != null && name != null && !name.isEmpty()) references.add(name);
    }

    public static Set<String> references(Builder<?> builder) {
        synchronized (RETRIES) {
            return RETRIES.getOrDefault(builder, Set.of());
        }
    }
}
