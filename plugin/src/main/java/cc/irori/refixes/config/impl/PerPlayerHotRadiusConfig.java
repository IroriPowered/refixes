package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class PerPlayerHotRadiusConfig extends Configuration<PerPlayerHotRadiusConfig> {

    public static final ConfigurationKey<PerPlayerHotRadiusConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<PerPlayerHotRadiusConfig, Integer> CHECK_INTERVAL_MS =
            new ConfigurationKey<>("CheckIntervalMs", ConfigField.INTEGER, 5000);
    public static final ConfigurationKey<PerPlayerHotRadiusConfig, Integer> MIN_RADIUS =
            new ConfigurationKey<>("MinRadius", ConfigField.INTEGER, 2);
    public static final ConfigurationKey<PerPlayerHotRadiusConfig, Integer> MAX_RADIUS =
            new ConfigurationKey<>("MaxRadius", ConfigField.INTEGER, 6);
    public static final ConfigurationKey<PerPlayerHotRadiusConfig, Float> TPS_LOW =
            new ConfigurationKey<>("TPSLow", ConfigField.FLOAT, 22.5f);
    public static final ConfigurationKey<PerPlayerHotRadiusConfig, Float> TPS_HIGH =
            new ConfigurationKey<>("TPSHigh", ConfigField.FLOAT, 27.0f);
    public static final ConfigurationKey<PerPlayerHotRadiusConfig, Integer> ADJUSTMENT_STEP =
            new ConfigurationKey<>("AdjustmentStep", ConfigField.INTEGER, 1);

    private static final PerPlayerHotRadiusConfig INSTANCE = new PerPlayerHotRadiusConfig();

    public PerPlayerHotRadiusConfig() {
        register(ENABLED, CHECK_INTERVAL_MS, MIN_RADIUS, MAX_RADIUS, TPS_LOW, TPS_HIGH, ADJUSTMENT_STEP);
    }

    public static PerPlayerHotRadiusConfig get() {
        return INSTANCE;
    }
}
