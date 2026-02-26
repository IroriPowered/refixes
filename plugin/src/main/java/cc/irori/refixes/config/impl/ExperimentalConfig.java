package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class ExperimentalConfig extends Configuration<ExperimentalConfig> {

    public static final ConfigurationKey<ExperimentalConfig, Boolean> PARALLEL_ENTITY_TICKING =
            new ConfigurationKey<>("ParallelEntityTicking", ConfigField.BOOLEAN, false);

    public static final ConfigurationKey<ExperimentalConfig, Boolean> PARALLEL_SPATIAL_COLLECTION =
            new ConfigurationKey<>("ParallelSpatialCollection", ConfigField.BOOLEAN, false);

    public static final ConfigurationKey<ExperimentalConfig, Integer> PARALLEL_STEERING_THRESHOLD =
            new ConfigurationKey<>("ParallelSteeringThreshold", ConfigField.INTEGER, 64);

    private static final ExperimentalConfig INSTANCE = new ExperimentalConfig();

    public ExperimentalConfig() {
        register(
                PARALLEL_ENTITY_TICKING,
                PARALLEL_SPATIAL_COLLECTION,
                PARALLEL_STEERING_THRESHOLD);
    }

    public static ExperimentalConfig get() {
        return INSTANCE;
    }
}
