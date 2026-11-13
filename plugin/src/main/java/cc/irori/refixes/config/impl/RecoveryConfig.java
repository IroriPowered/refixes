package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;

public class RecoveryConfig extends Configuration<RecoveryConfig> {

    public static final ConfigurationKey<RecoveryConfig, UnknownBlockCleanerConfig> UNKNOWN_BLOCK_CLEANER_CONFIG =
            ConfigurationKey.subConfig("UnknownBlockCleaner", UnknownBlockCleanerConfig.get());
    public static final ConfigurationKey<RecoveryConfig, WatchdogRecoveryConfig> WATCHDOG_CONFIG =
            ConfigurationKey.subConfig("Watchdog", WatchdogRecoveryConfig.get());

    private static final RecoveryConfig INSTANCE = new RecoveryConfig();

    public RecoveryConfig() {
        register(UNKNOWN_BLOCK_CLEANER_CONFIG, WATCHDOG_CONFIG);
    }

    public static RecoveryConfig get() {
        return INSTANCE;
    }
}
