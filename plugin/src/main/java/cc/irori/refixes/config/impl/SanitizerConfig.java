package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class SanitizerConfig extends Configuration<SanitizerConfig> {

    public static final ConfigurationKey<SanitizerConfig, Boolean> DEFAULT_WORLD_WATCHER =
            new ConfigurationKey<>("DefaultWorldWatcher", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<SanitizerConfig, Boolean> RESPAWN_BLOCK =
            new ConfigurationKey<>("RespawnBlock", ConfigField.BOOLEAN, true);

    private static final SanitizerConfig INSTANCE = new SanitizerConfig();

    public SanitizerConfig() {
        register(DEFAULT_WORLD_WATCHER, RESPAWN_BLOCK);
    }

    public static SanitizerConfig get() {
        return INSTANCE;
    }
}
