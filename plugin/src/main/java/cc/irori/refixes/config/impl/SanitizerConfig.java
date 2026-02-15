package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class SanitizerConfig extends Configuration<SanitizerConfig> {

    public static final ConfigurationKey<SanitizerConfig, Boolean> DEFAULT_WORLD_RECOVERY =
            new ConfigurationKey<>("DefaultWorldRecovery", ConfigField.BOOLEAN, true);

    private static final SanitizerConfig INSTANCE = new SanitizerConfig();

    public SanitizerConfig() {
        register(DEFAULT_WORLD_RECOVERY);
    }

    public static SanitizerConfig get() {
        return INSTANCE;
    }
}
