package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class TickThrottledCleanupConfig extends Configuration<TickThrottledCleanupConfig> {

    public static final ConfigurationKey<TickThrottledCleanupConfig, Boolean> CLEANUP_FROZEN_ENTITIES =
            new ConfigurationKey<>("CleanupFrozenEntities", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<TickThrottledCleanupConfig, String[]> CLEANUP_EXCLUDED_NPC_TYPES =
            new ConfigurationKey<>("CleanupExcludedNpcTypes", ConfigField.STRING_ARRAY, new String[0]);
    public static final ConfigurationKey<TickThrottledCleanupConfig, Boolean> LEGACY_CLEANUP =
            new ConfigurationKey<>("LegacyCleanup", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<TickThrottledCleanupConfig, String[]> LEGACY_CLEANUP_EXCLUDED_NPC_TYPES =
            new ConfigurationKey<>("LegacyCleanupExcludedNpcTypes", ConfigField.STRING_ARRAY, new String[0]);

    private static final TickThrottledCleanupConfig INSTANCE = new TickThrottledCleanupConfig();

    public TickThrottledCleanupConfig() {
        register(
                CLEANUP_FROZEN_ENTITIES, CLEANUP_EXCLUDED_NPC_TYPES, LEGACY_CLEANUP, LEGACY_CLEANUP_EXCLUDED_NPC_TYPES);
    }

    public static TickThrottledCleanupConfig get() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return getValue(CLEANUP_FROZEN_ENTITIES) || getValue(LEGACY_CLEANUP);
    }
}
