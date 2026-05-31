package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class IdleWorldPauseConfig extends Configuration<IdleWorldPauseConfig> {

    public static final ConfigurationKey<IdleWorldPauseConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<IdleWorldPauseConfig, Integer> CHECK_INTERVAL_MS =
            new ConfigurationKey<>("CheckIntervalMs", ConfigField.INTEGER, 10000);
    public static final ConfigurationKey<IdleWorldPauseConfig, String[]> EXCLUDED_WORLDS =
            new ConfigurationKey<>("ExcludedWorlds", ConfigField.STRING_ARRAY, new String[0]);

    private static final IdleWorldPauseConfig INSTANCE = new IdleWorldPauseConfig();

    public IdleWorldPauseConfig() {
        register(ENABLED, CHECK_INTERVAL_MS, EXCLUDED_WORLDS);
    }

    public static IdleWorldPauseConfig get() {
        return INSTANCE;
    }
}
