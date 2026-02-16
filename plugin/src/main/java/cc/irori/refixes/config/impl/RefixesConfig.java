package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;

public class RefixesConfig extends Configuration<RefixesConfig> {

    private static final ConfigurationKey<RefixesConfig, ListenerConfig> LISTENER_CONFIG =
            ConfigurationKey.subConfig("Listeners", ListenerConfig.get());
    private static final ConfigurationKey<RefixesConfig, SystemConfig> SYSTEM_CONFIG =
            ConfigurationKey.subConfig("Systems", SystemConfig.get());

    private static final RefixesConfig INSTANCE = new RefixesConfig();

    public RefixesConfig() {
        register(LISTENER_CONFIG, SYSTEM_CONFIG);
    }

    public static RefixesConfig get() {
        return INSTANCE;
    }
}
