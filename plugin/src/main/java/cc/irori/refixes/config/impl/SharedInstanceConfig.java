package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class SharedInstanceConfig extends Configuration<SharedInstanceConfig> {

    public static final ConfigurationKey<SharedInstanceConfig, String[]> EXCLUDED_PREFIXES =
            new ConfigurationKey<>("ExcludedPrefixes", ConfigField.STRING_ARRAY, new String[0]);
    public static final ConfigurationKey<SharedInstanceConfig, Boolean> RESET_ON_EMPTY =
            new ConfigurationKey<>("ResetOnEmpty", ConfigField.BOOLEAN, false);

    private static final SharedInstanceConfig INSTANCE = new SharedInstanceConfig();

    public SharedInstanceConfig() {
        register(EXCLUDED_PREFIXES, RESET_ON_EMPTY);
    }

    public static SharedInstanceConfig get() {
        return INSTANCE;
    }
}
