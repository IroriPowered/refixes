package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class UnknownBlockCleanerConfig extends Configuration<UnknownBlockCleanerConfig> {

    public static final ConfigurationKey<UnknownBlockCleanerConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<UnknownBlockCleanerConfig, Boolean> SCAN_FLUIDS =
            new ConfigurationKey<>("ScanFluids", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<UnknownBlockCleanerConfig, Boolean> SCAN_CONTAINERS =
            new ConfigurationKey<>("ScanContainers", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<UnknownBlockCleanerConfig, Boolean> SCAN_PLAYER_INVENTORIES =
            new ConfigurationKey<>("ScanPlayerInventories", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<UnknownBlockCleanerConfig, String[]> EXCLUDE =
            new ConfigurationKey<>("Exclude", ConfigField.STRING_ARRAY, new String[0]);
    public static final ConfigurationKey<UnknownBlockCleanerConfig, Integer> BUDGET_MS =
            new ConfigurationKey<>("BudgetMs", ConfigField.INTEGER, 10);
    public static final ConfigurationKey<UnknownBlockCleanerConfig, Integer> INTERVAL_MS =
            new ConfigurationKey<>("IntervalMs", ConfigField.INTEGER, 50);

    private static final UnknownBlockCleanerConfig INSTANCE = new UnknownBlockCleanerConfig();

    public UnknownBlockCleanerConfig() {
        register(ENABLED, SCAN_FLUIDS, SCAN_CONTAINERS, SCAN_PLAYER_INVENTORIES, EXCLUDE, BUDGET_MS, INTERVAL_MS);
    }

    public static UnknownBlockCleanerConfig get() {
        return INSTANCE;
    }
}
