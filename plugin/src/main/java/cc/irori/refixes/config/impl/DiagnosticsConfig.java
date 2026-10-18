package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class DiagnosticsConfig extends Configuration<DiagnosticsConfig> {

    public static final ConfigurationKey<DiagnosticsConfig, Boolean> HYBOX_INTEGRATION =
            new ConfigurationKey<>("HyboxIntegration", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<DiagnosticsConfig, WatchdogConfig> WATCHDOG_CONFIG =
            ConfigurationKey.subConfig("Watchdog", WatchdogConfig.get());
    private static final DiagnosticsConfig INSTANCE = new DiagnosticsConfig();

    public DiagnosticsConfig() {
        register(HYBOX_INTEGRATION, WATCHDOG_CONFIG);
    }

    public static DiagnosticsConfig get() {
        return INSTANCE;
    }
}
