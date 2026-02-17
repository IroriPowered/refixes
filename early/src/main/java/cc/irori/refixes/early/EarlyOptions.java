package cc.irori.refixes.early;

import java.util.function.Supplier;

public final class EarlyOptions {

    private static boolean available = false;

    /* Fluid Pre-processing */
    public static final Value<Boolean> DISABLE_FLUID_PRE_PROCESS = new Value<>();

    /* Cylinder Visibility */
    public static final Value<Boolean> CYLINDER_VISIBILITY_ENABLED = new Value<>();
    public static final Value<Double> CYLINDER_VISIBILITY_HEIGHT_MULTIPLIER = new Value<>();

    /* Parallel Entity Ticking */
    public static final Value<Boolean> PARALLEL_ENTITY_TICKING = new Value<>();

    /* KDTree Optimization */
    public static final Value<Boolean> KDTREE_OPTIMIZATION_OPTIMIZE_SORT = new Value<>();
    public static final Value<Integer> KDTREE_OPTIMIZATION_THRESHOLD = new Value<>();

    // Private constructor to prevent instantiation
    private EarlyOptions() {}

    public static void setAvailable(boolean available) {
        EarlyOptions.available = available;
    }

    public static boolean isAvailable() {
        return available;
    }

    public static final class Value<T> {

        private Supplier<T> supplier = null;

        private Value() {}

        public void setSupplier(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        public T get() {
            if (supplier == null) {
                throw new IllegalStateException("Value supplier has not been set");
            }
            return supplier.get();
        }
    }
}
