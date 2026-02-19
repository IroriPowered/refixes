package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class IdlePlayerHandlerConfig extends Configuration<IdlePlayerHandlerConfig> {

    public static final ConfigurationKey<IdlePlayerHandlerConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<IdlePlayerHandlerConfig, Integer> IDLE_TIMEOUT_SECONDS =
            new ConfigurationKey<>("IdleTimeoutSeconds", ConfigField.INTEGER, 90);
    public static final ConfigurationKey<IdlePlayerHandlerConfig, Integer> CHECK_INTERVAL_SECONDS =
            new ConfigurationKey<>("CheckIntervalSeconds", ConfigField.INTEGER, 10);
    public static final ConfigurationKey<IdlePlayerHandlerConfig, Boolean> REDUCE_VIEW_RADIUS =
            new ConfigurationKey<>("ReduceViewRadius", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<IdlePlayerHandlerConfig, Integer> IDLE_VIEW_RADIUS =
            new ConfigurationKey<>("IdleViewRadius", ConfigField.INTEGER, 4);
    public static final ConfigurationKey<IdlePlayerHandlerConfig, Boolean> REDUCE_HOT_RADIUS =
            new ConfigurationKey<>("ReduceHotRadius", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<IdlePlayerHandlerConfig, Integer> IDLE_HOT_RADIUS =
            new ConfigurationKey<>("IdleHotRadius", ConfigField.INTEGER, 3);
    public static final ConfigurationKey<IdlePlayerHandlerConfig, Boolean> REDUCE_MIN_LOADED_RADIUS =
            new ConfigurationKey<>("ReduceMinLoadedRadius", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<IdlePlayerHandlerConfig, Integer> IDLE_MIN_LOADED_RADIUS =
            new ConfigurationKey<>("IdleMinLoadedRadius", ConfigField.INTEGER, 2);
    public static final ConfigurationKey<IdlePlayerHandlerConfig, Double> MOVEMENT_THRESHOLD =
            new ConfigurationKey<>("MovementThreshold", ConfigField.DOUBLE, 0.5);

    private static final IdlePlayerHandlerConfig INSTANCE = new IdlePlayerHandlerConfig();

    public IdlePlayerHandlerConfig() {
        register(
                ENABLED,
                IDLE_TIMEOUT_SECONDS,
                CHECK_INTERVAL_SECONDS,
                REDUCE_VIEW_RADIUS,
                IDLE_VIEW_RADIUS,
                REDUCE_HOT_RADIUS,
                IDLE_HOT_RADIUS,
                REDUCE_MIN_LOADED_RADIUS,
                IDLE_MIN_LOADED_RADIUS,
                MOVEMENT_THRESHOLD);
    }

    public static IdlePlayerHandlerConfig get() {
        return INSTANCE;
    }
}
