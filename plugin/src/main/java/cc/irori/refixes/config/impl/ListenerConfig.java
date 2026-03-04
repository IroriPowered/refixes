package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class ListenerConfig extends Configuration<ListenerConfig> {

    public static final ConfigurationKey<ListenerConfig, Boolean> INSTANCE_POSITION_TRACKER =
            new ConfigurationKey<>("InstancePositionTracker", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<ListenerConfig, Boolean> UNKNOWN_BLOCK_CLEANER =
            new ConfigurationKey<>("UnknownBlockCleaner", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<ListenerConfig, Integer> UNKNOWN_BLOCK_CLEANER_BUDGET_MS =
            new ConfigurationKey<>("UnknownBlockCleanerBudgetMs", ConfigField.INTEGER, 10);
    public static final ConfigurationKey<ListenerConfig, Integer> UNKNOWN_BLOCK_CLEANER_INTERVAL_MS =
            new ConfigurationKey<>("UnknownBlockCleanerIntervalMs", ConfigField.INTEGER, 50);

    private static final ListenerConfig INSTANCE = new ListenerConfig();

    public ListenerConfig() {
        register(
                INSTANCE_POSITION_TRACKER,
                UNKNOWN_BLOCK_CLEANER,
                UNKNOWN_BLOCK_CLEANER_BUDGET_MS,
                UNKNOWN_BLOCK_CLEANER_INTERVAL_MS);
    }

    public static ListenerConfig get() {
        return INSTANCE;
    }
}
