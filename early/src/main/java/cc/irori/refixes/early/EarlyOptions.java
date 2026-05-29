package cc.irori.refixes.early;

import java.util.function.Supplier;

public final class EarlyOptions {

    private static boolean available = false;

    /* Force Skip Mod Validation */
    public static final Value<Boolean> FORCE_SKIP_MOD_VALIDATION = new Value<>(false);

    /* Cylinder Visibility */
    public static final Value<Double> CYLINDER_VISIBILITY_HEIGHT_MULTIPLIER = new Value<>(2.0);

    /* ChunkTracker Rate Limits */
    public static final Value<Integer> MAX_CHUNKS_PER_SECOND = new Value<>(36);
    public static final Value<Integer> MAX_CHUNKS_PER_TICK = new Value<>(4);
    public static final Value<Integer> CHUNK_UNLOAD_OFFSET = new Value<>(4);
    public static final Value<Boolean> VANILLA_KEEP_SPAWN_LOADED = new Value<>(true);

    /* KDTree Optimization */
    public static final Value<Integer> KDTREE_OPTIMIZATION_THRESHOLD = new Value<>(64);

    /* Shared Instances */
    public static final Value<String[]> SHARED_INSTANCES_EXCLUDED_PREFIXES = new Value<>(new String[0]);

    /* Block Entity Sleep */
    public static final Value<Integer> BLOCK_ENTITY_SLEEP_INTERVAL = new Value<>(4);

    /* Stat Recalculation Throttle */
    public static final Value<Integer> STAT_RECALC_INTERVAL = new Value<>(4);

    /* Pathfinding */
    public static final Value<Integer> PATHFINDING_MAX_PATH_LENGTH = new Value<>(200);
    public static final Value<Integer> PATHFINDING_OPEN_NODES_LIMIT = new Value<>(80);
    public static final Value<Integer> PATHFINDING_TOTAL_NODES_LIMIT = new Value<>(400);

    /* Parallel Steering Threshold */
    public static final Value<Integer> PARALLEL_STEERING_THRESHOLD = new Value<>(64);

    private EarlyOptions() {}

    public static void setAvailable(boolean available) {
        EarlyOptions.available = available;
    }

    public static boolean isAvailable() {
        return available;
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
