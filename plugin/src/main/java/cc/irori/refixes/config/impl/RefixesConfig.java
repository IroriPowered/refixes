package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;

public class RefixesConfig extends Configuration<RefixesConfig> {

    private static final ConfigurationKey<RefixesConfig, EarlyConfig> EARLY_CONFIG =
            ConfigurationKey.subConfig("Early", EarlyConfig.get());
    private static final ConfigurationKey<RefixesConfig, ListenerConfig> LISTENER_CONFIG =
            ConfigurationKey.subConfig("Listeners", ListenerConfig.get());
    private static final ConfigurationKey<RefixesConfig, SystemConfig> SYSTEM_CONFIG =
            ConfigurationKey.subConfig("Systems", SystemConfig.get());
    private static final ConfigurationKey<RefixesConfig, ServiceConfig> SERVICE_CONFIG =
            ConfigurationKey.subConfig("Services", ServiceConfig.get());
    private static final ConfigurationKey<RefixesConfig, SharedInstanceConfig> SHARED_INSTANCE_CONFIG =
            ConfigurationKey.subConfig("SharedInstanceWorlds", SharedInstanceConfig.get());
    private static final ConfigurationKey<RefixesConfig, WatchdogConfig> WATCHDOG_CONFIG =
            ConfigurationKey.subConfig("Watchdog", WatchdogConfig.get());
    private static final ConfigurationKey<RefixesConfig, ExperimentalConfig> EXPERIMENTAL_CONFIG =
            ConfigurationKey.subConfig("Experimental", ExperimentalConfig.get());

    private static final RefixesConfig INSTANCE = new RefixesConfig();

    public RefixesConfig() {
        register(
                EARLY_CONFIG,
                LISTENER_CONFIG,
                SYSTEM_CONFIG,
                SERVICE_CONFIG,
                SHARED_INSTANCE_CONFIG,
                WATCHDOG_CONFIG,
                EXPERIMENTAL_CONFIG);
    }

    public static RefixesConfig get() {
        return INSTANCE;
    }
}
