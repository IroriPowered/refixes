package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;

public class FeaturesConfig extends Configuration<FeaturesConfig> {

    public static final ConfigurationKey<FeaturesConfig, ChunkLoaderConfig> CHUNK_LOADER_CONFIG =
            ConfigurationKey.subConfig("ChunkLoader", ChunkLoaderConfig.get());
    public static final ConfigurationKey<FeaturesConfig, SharedInstanceConfig> SHARED_INSTANCES_CONFIG =
            ConfigurationKey.subConfig("SharedInstances", SharedInstanceConfig.get());

    private static final FeaturesConfig INSTANCE = new FeaturesConfig();

    public FeaturesConfig() {
        register(CHUNK_LOADER_CONFIG, SHARED_INSTANCES_CONFIG);
    }

    public static FeaturesConfig get() {
        return INSTANCE;
    }
}
