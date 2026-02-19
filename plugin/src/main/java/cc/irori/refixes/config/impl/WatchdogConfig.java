package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class WatchdogConfig extends Configuration<WatchdogConfig> {

    public static final ConfigurationKey<WatchdogConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<WatchdogConfig, Boolean> SHUTDOWN_ON_DEFAULT_WORLD_CRASH =
            new ConfigurationKey<>("ShutdownOnDefaultWorldCrash", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<WatchdogConfig, Boolean> AUTO_RESTART_WORLDS =
            new ConfigurationKey<>("AutoRestartWorlds", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<WatchdogConfig, Boolean> DUMP_ALL_THREADS =
            new ConfigurationKey<>("DumpAllThreads", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<WatchdogConfig, Integer> ACTIVATION_DELAY_MS =
            new ConfigurationKey<>("ActivationDelayMs", ConfigField.INTEGER, 10_000);
    public static final ConfigurationKey<WatchdogConfig, Integer> THREAD_TIMEOUT_MS =
            new ConfigurationKey<>("ThreadTimeoutMs", ConfigField.INTEGER, 30_000);
    public static final ConfigurationKey<WatchdogConfig, Integer> SHUTDOWN_TIMEOUT_MS =
            new ConfigurationKey<>("ShutdownTimeoutMs", ConfigField.INTEGER, 60_000);
    public static final ConfigurationKey<WatchdogConfig, String[]> AUTO_RESTARTING_WORLD_FILTER =
            new ConfigurationKey<>("AutoRestartingWorldFilter", ConfigField.STRING_ARRAY, new String[0]);

    private static final WatchdogConfig INSTANCE = new WatchdogConfig();

    public WatchdogConfig() {
        register(
                ENABLED,
                SHUTDOWN_ON_DEFAULT_WORLD_CRASH,
                AUTO_RESTART_WORLDS,
                DUMP_ALL_THREADS,
                ACTIVATION_DELAY_MS,
                THREAD_TIMEOUT_MS,
                SHUTDOWN_TIMEOUT_MS,
                AUTO_RESTARTING_WORLD_FILTER);
    }

    public static WatchdogConfig get() {
        return INSTANCE;
    }
}
