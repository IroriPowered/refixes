package cc.irori.refixes.early;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class EarlyOptions {

    private static boolean available = false;
    private static final String MIXIN_SELECTION_PROPERTY = "cc.irori.refixes.early.enabledMixins";

    private record MixinSelection(String encoded, Set<String> mixins) {}

    private static volatile MixinSelection selection = new MixinSelection(null, Set.of());

    /* Cylinder Visibility */
    public static final Value<Double> CYLINDER_VISIBILITY_HEIGHT_MULTIPLIER = new Value<>(2.0);
    public static final Value<Integer> MAX_SECTIONS_PER_SECOND = new Value<>(360);
    public static final Value<Integer> MAX_SECTIONS_PER_TICK = new Value<>(40);
    public static final Value<Integer> CHUNK_UNLOAD_OFFSET = new Value<>(4);
    public static final Value<Boolean> VANILLA_KEEP_SPAWN_LOADED = new Value<>(true);

    /* KDTree Optimization */
    public static final Value<Integer> KDTREE_OPTIMIZATION_THRESHOLD = new Value<>(64);

    /* Shared Instances */
    public static final Value<Boolean> SHARED_INSTANCES_STORAGE_AVAILABLE = new Value<>(false);
    public static final Value<String[]> SHARED_INSTANCES_EXCLUDED_PREFIXES = new Value<>(new String[0]);
    public static final Value<Boolean> SHARED_INSTANCES_RESET_ON_EMPTY = new Value<>(false);

    /* Pathfinding */
    public static final Value<Integer> PATHFINDING_MAX_PATH_LENGTH = new Value<>(200);
    public static final Value<Integer> PATHFINDING_OPEN_NODES_LIMIT = new Value<>(80);
    public static final Value<Integer> PATHFINDING_TOTAL_NODES_LIMIT = new Value<>(400);
    public static final Value<Integer> PATHFINDING_MAX_NEW_SEARCHES_PER_TICK = new Value<>(8);
    public static final Value<Integer> PATHFINDING_MAX_NODE_EXPANSIONS_PER_TICK = new Value<>(600);
    public static final Value<Integer> SHUTDOWN_SAVE_TIMEOUT_SECONDS = new Value<>(10);

    private EarlyOptions() {}

    public static void setAvailable(boolean available) {
        EarlyOptions.available = available;
    }

    public static boolean isAvailable() {
        return available;
    }

    static void setMixinSelection(Set<String> selected) {
        Set<String> immutable = Set.copyOf(selected);
        String encoded = immutable.stream().sorted().collect(Collectors.joining("\n"));
        System.setProperty(MIXIN_SELECTION_PROPERTY, encoded);
        selection = new MixinSelection(encoded, immutable);
    }

    static void clearMixinSelection() {
        System.clearProperty(MIXIN_SELECTION_PROPERTY);
        selection = new MixinSelection(null, Set.of());
    }

    public static boolean hasMixinSelection() {
        return System.getProperty(MIXIN_SELECTION_PROPERTY) != null;
    }

    public static boolean isMixinEnabled(String simpleName) {
        if (simpleName == null) return false;
        String encoded = System.getProperty(MIXIN_SELECTION_PROPERTY);
        if (encoded == null) return false;
        MixinSelection current = selection;
        if (!encoded.equals(current.encoded)) {
            Set<String> selected = new HashSet<>();
            for (String mixin : encoded.split("\\n")) {
                if (!mixin.isEmpty()) selected.add(mixin);
            }
            current = new MixinSelection(encoded, Set.copyOf(selected));
            selection = current;
        }
        return current.mixins.contains(simpleName);
    }

    public static final class Value<T> {

        private final T defaultValue;
        private Supplier<T> supplier = null;

        private Value(T defaultValue) {
            this.defaultValue = defaultValue;
        }

        public void setSupplier(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        public boolean isSet() {
            return supplier != null;
        }

        // Falls back to the default when no supplier was wired (e.g. classloader split
        // between the early and main plugin halves means the main plugin's setSupplier
        // wrote to a different copy of this class).
        public T get() {
            return supplier != null ? supplier.get() : defaultValue;
        }
    }
}
