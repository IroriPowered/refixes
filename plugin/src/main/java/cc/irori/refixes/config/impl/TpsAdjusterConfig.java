package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class TpsAdjusterConfig extends Configuration<TpsAdjusterConfig> {

    public static final ConfigurationKey<TpsAdjusterConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<TpsAdjusterConfig, Integer> TPS_LIMIT =
            new ConfigurationKey<>("TpsLimit", ConfigField.INTEGER, 20);
    public static final ConfigurationKey<TpsAdjusterConfig, Integer> HIBERNATE_TPS =
            new ConfigurationKey<>("HibernateTps", ConfigField.INTEGER, 5);
    public static final ConfigurationKey<TpsAdjusterConfig, String[]> WORLD_FILTER =
            new ConfigurationKey<>("WorldFilter", ConfigField.STRING_ARRAY, new String[0]);
    public static final ConfigurationKey<TpsAdjusterConfig, Integer> CHECK_INTERVAL_MS =
            new ConfigurationKey<>("CheckIntervalMs", ConfigField.INTEGER, 5000);
    public static final ConfigurationKey<TpsAdjusterConfig, Integer> HIBERNATE_DELAY_MS =
            new ConfigurationKey<>("HibernateDelayMs", ConfigField.INTEGER, 300_000);

    private static final TpsAdjusterConfig INSTANCE = new TpsAdjusterConfig();

    public TpsAdjusterConfig() {
        register(ENABLED, TPS_LIMIT, HIBERNATE_TPS, WORLD_FILTER, CHECK_INTERVAL_MS, HIBERNATE_DELAY_MS);
    }

    public static TpsAdjusterConfig get() {
        return INSTANCE;
    }
}
