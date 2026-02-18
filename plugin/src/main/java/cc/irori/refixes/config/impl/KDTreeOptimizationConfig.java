package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class KDTreeOptimizationConfig extends Configuration<KDTreeOptimizationConfig> {

    public static final ConfigurationKey<KDTreeOptimizationConfig, Boolean> OPTIMIZE_KDTREE_SORT =
            new ConfigurationKey<>("OptimizeKDTreeSort", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<KDTreeOptimizationConfig, Integer> SPATIAL_FAST_SORT_THRESHOLD =
            new ConfigurationKey<>("SpatialFastSortThreshold", ConfigField.INTEGER, 64);

    private static final KDTreeOptimizationConfig INSTANCE = new KDTreeOptimizationConfig();

    public KDTreeOptimizationConfig() {
        register(OPTIMIZE_KDTREE_SORT, SPATIAL_FAST_SORT_THRESHOLD);
    }

    public static KDTreeOptimizationConfig get() {
        return INSTANCE;
    }
}
