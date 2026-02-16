package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class ViewRadiusAdjusterConfig extends Configuration<ViewRadiusAdjusterConfig> {

    public static final ConfigurationKey<ViewRadiusAdjusterConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<ViewRadiusAdjusterConfig, Integer> MIN_RADIUS =
            new ConfigurationKey<>("MinRadius", ConfigField.INTEGER, 2);
    public static final ConfigurationKey<ViewRadiusAdjusterConfig, Integer> MAX_RADIUS =
            new ConfigurationKey<>("MaxRadius", ConfigField.INTEGER, 10);
    public static final ConfigurationKey<ViewRadiusAdjusterConfig, Float> DECREASE_RADIUS_TPS =
            new ConfigurationKey<>("DecreaseRadiusTps", ConfigField.FLOAT, 18.0f);
    public static final ConfigurationKey<ViewRadiusAdjusterConfig, Float> INCREASE_RADIUS_TPS =
            new ConfigurationKey<>("IncreaseRadiusTps", ConfigField.FLOAT, 19.5f);
    public static final ConfigurationKey<ViewRadiusAdjusterConfig, Integer> CHECK_INTERVAL_MS =
            new ConfigurationKey<>("CheckIntervalMs", ConfigField.INTEGER, 5000);

    private static final ViewRadiusAdjusterConfig INSTANCE = new ViewRadiusAdjusterConfig();

    public ViewRadiusAdjusterConfig() {
        register(ENABLED, MIN_RADIUS, MAX_RADIUS, DECREASE_RADIUS_TPS, INCREASE_RADIUS_TPS, CHECK_INTERVAL_MS);
    }

    public static ViewRadiusAdjusterConfig get() {
        return INSTANCE;
    }
}
