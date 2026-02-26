package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class ExperimentalConfig extends Configuration<ExperimentalConfig> {

    public static final ConfigurationKey<ExperimentalConfig, Boolean> PARALLEL_ENTITY_TICKING =
            new ConfigurationKey<>("ParallelEntityTicking", ConfigField.BOOLEAN, false);

    public static final ConfigurationKey<ExperimentalConfig, Integer> CHUNK_GENERATION_PERMITS =
            new ConfigurationKey<>("ChunkGenerationPermits", ConfigField.INTEGER, 1);

    private static final ExperimentalConfig INSTANCE = new ExperimentalConfig();

    public ExperimentalConfig() {
        register(PARALLEL_ENTITY_TICKING, CHUNK_GENERATION_PERMITS);
    }

    public static ExperimentalConfig get() {
        return INSTANCE;
    }
}
