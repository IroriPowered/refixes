package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class WatchdogRecoveryConfig extends Configuration<WatchdogRecoveryConfig> {

    public static final ConfigurationKey<WatchdogRecoveryConfig, Boolean> SHUTDOWN_ON_DEFAULT_WORLD_CRASH =
            new ConfigurationKey<>("ShutdownOnDefaultWorldCrash", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<WatchdogRecoveryConfig, Boolean> AUTO_RESTART_WORLDS =
            new ConfigurationKey<>("AutoRestartWorlds", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<WatchdogRecoveryConfig, Boolean> HALT_ON_SHUTDOWN_TIMEOUT =
            new ConfigurationKey<>("HaltOnShutdownTimeout", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<WatchdogRecoveryConfig, Integer> SHUTDOWN_TIMEOUT_MS =
            new ConfigurationKey<>("ShutdownTimeoutMs", ConfigField.INTEGER, 60_000);
    public static final ConfigurationKey<WatchdogRecoveryConfig, Integer> RESTART_SAVE_TIMEOUT_MS =
            new ConfigurationKey<>("RestartSaveTimeoutMs", ConfigField.INTEGER, 15_000);
    public static final ConfigurationKey<WatchdogRecoveryConfig, String[]> AUTO_RESTARTING_WORLD_FILTER =
            new ConfigurationKey<>("AutoRestartingWorldFilter", ConfigField.STRING_ARRAY, new String[0]);

    private static final WatchdogRecoveryConfig INSTANCE = new WatchdogRecoveryConfig();

    public WatchdogRecoveryConfig() {
        register(
                SHUTDOWN_ON_DEFAULT_WORLD_CRASH,
                AUTO_RESTART_WORLDS,
                HALT_ON_SHUTDOWN_TIMEOUT,
                SHUTDOWN_TIMEOUT_MS,
                RESTART_SAVE_TIMEOUT_MS,
                AUTO_RESTARTING_WORLD_FILTER);
    }

    public boolean isEnabled() {
        return getValue(SHUTDOWN_ON_DEFAULT_WORLD_CRASH)
                || getValue(AUTO_RESTART_WORLDS)
                || getValue(HALT_ON_SHUTDOWN_TIMEOUT);
    }

    public static WatchdogRecoveryConfig get() {
        return INSTANCE;
    }
}
