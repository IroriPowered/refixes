package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class CompatibilityConfig extends Configuration<CompatibilityConfig> {

    public static final ConfigurationKey<CompatibilityConfig, Boolean> EXISTING_SHARED_INSTANCES =
            new ConfigurationKey<>("ExistingSharedInstances", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<CompatibilityConfig, TickThrottledCleanupConfig>
            TICK_THROTTLED_CLEANUP_CONFIG =
                    ConfigurationKey.subConfig("TickThrottledCleanup", TickThrottledCleanupConfig.get());

    private static final CompatibilityConfig INSTANCE = new CompatibilityConfig();

    public CompatibilityConfig() {
        register(EXISTING_SHARED_INSTANCES, TICK_THROTTLED_CLEANUP_CONFIG);
    }

    public static CompatibilityConfig get() {
        return INSTANCE;
    }
}
