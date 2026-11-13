package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;

public class RefixesConfig extends Configuration<RefixesConfig> {

    private static final ConfigurationKey<RefixesConfig, EarlyConfig> EARLY_CONFIG =
            ConfigurationKey.subConfig("Early", EarlyConfig.get());
    private static final ConfigurationKey<RefixesConfig, RuntimeConfig> RUNTIME_CONFIG =
            ConfigurationKey.subConfig("Runtime", RuntimeConfig.get());

    private static final RefixesConfig INSTANCE = new RefixesConfig();

    public RefixesConfig() {
        register(EARLY_CONFIG, RUNTIME_CONFIG);
    }

    public static RefixesConfig get() {
        return INSTANCE;
    }
}
