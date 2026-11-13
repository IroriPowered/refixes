package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class WatchdogConfig extends Configuration<WatchdogConfig> {

    public static final ConfigurationKey<WatchdogConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<WatchdogConfig, Boolean> DUMP_ALL_THREADS =
            new ConfigurationKey<>("DumpAllThreads", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<WatchdogConfig, Integer> ACTIVATION_DELAY_MS =
            new ConfigurationKey<>("ActivationDelayMs", ConfigField.INTEGER, 10_000);
    public static final ConfigurationKey<WatchdogConfig, Integer> THREAD_TIMEOUT_MS =
            new ConfigurationKey<>("ThreadTimeoutMs", ConfigField.INTEGER, 30_000);

    private static final WatchdogConfig INSTANCE = new WatchdogConfig();

    public WatchdogConfig() {
        register(ENABLED, DUMP_ALL_THREADS, ACTIVATION_DELAY_MS, THREAD_TIMEOUT_MS);
    }

    public boolean shouldRegisterService() {
        return getValue(ENABLED) || WatchdogRecoveryConfig.get().isEnabled();
    }

    public static WatchdogConfig get() {
        return INSTANCE;
    }
}
