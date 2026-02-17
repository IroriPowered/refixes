package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class SharedInstanceConfig extends Configuration<SharedInstanceConfig> {

    public static final ConfigurationKey<SharedInstanceConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<SharedInstanceConfig, String[]> EXCLUDED_PREFIXES =
            new ConfigurationKey<>("ExcludedPrefixes", ConfigField.STRING_ARRAY, new String[0]);

    private static final SharedInstanceConfig INSTANCE = new SharedInstanceConfig();

    public SharedInstanceConfig() {
        register(ENABLED, EXCLUDED_PREFIXES);
    }

    public static SharedInstanceConfig get() {
        return INSTANCE;
    }
}
