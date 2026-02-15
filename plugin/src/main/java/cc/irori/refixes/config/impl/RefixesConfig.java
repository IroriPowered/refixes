package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;

public class RefixesConfig extends Configuration<RefixesConfig> {

    private static final ConfigurationKey<RefixesConfig, SanitizerConfig> SANITIZER_CONFIG =
            ConfigurationKey.subConfig("SanitizerConfig", SanitizerConfig.get());

    private static final RefixesConfig INSTANCE = new RefixesConfig();

    public RefixesConfig() {
        register(SANITIZER_CONFIG);
    }

    public static RefixesConfig get() {
        return INSTANCE;
    }
}
