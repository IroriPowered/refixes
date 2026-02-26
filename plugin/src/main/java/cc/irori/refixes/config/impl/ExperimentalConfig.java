package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class ExperimentalConfig extends Configuration<ExperimentalConfig> {

    public static final ConfigurationKey<ExperimentalConfig, Boolean> PARALLEL_ENTITY_TICKING =
            new ConfigurationKey<>("ParallelEntityTicking", ConfigField.BOOLEAN, false);

    public static final ConfigurationKey<ExperimentalConfig, Boolean> CORRUPT_SECTION_PROTECTION =
            new ConfigurationKey<>("CorruptSectionProtection", ConfigField.BOOLEAN, true);

    private static final ExperimentalConfig INSTANCE = new ExperimentalConfig();

    public ExperimentalConfig() {
        register(PARALLEL_ENTITY_TICKING, CORRUPT_SECTION_PROTECTION);
    }

    public static ExperimentalConfig get() {
        return INSTANCE;
    }
}
