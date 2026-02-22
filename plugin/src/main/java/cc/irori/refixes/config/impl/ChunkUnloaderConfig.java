package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class ChunkUnloaderConfig extends Configuration<ChunkUnloaderConfig> {

    public static final ConfigurationKey<ChunkUnloaderConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<ChunkUnloaderConfig, Integer> UNLOAD_DISTANCE_OFFSET =
            new ConfigurationKey<>("UnloadDistanceOffset", ConfigField.INTEGER, 4);
    public static final ConfigurationKey<ChunkUnloaderConfig, Integer> UNLOAD_DELAY_SECONDS =
            new ConfigurationKey<>("UnloadDelaySeconds", ConfigField.INTEGER, 30);
    public static final ConfigurationKey<ChunkUnloaderConfig, Integer> MAX_UNLOADS_PER_RUN =
            new ConfigurationKey<>("MaxUnloadsPerRun", ConfigField.INTEGER, 200);
    public static final ConfigurationKey<ChunkUnloaderConfig, Integer> MIN_LOADED_CHUNKS =
            new ConfigurationKey<>("MinLoadedChunks", ConfigField.INTEGER, 0);
    public static final ConfigurationKey<ChunkUnloaderConfig, Integer> CHECK_INTERVAL_MS =
            new ConfigurationKey<>("CheckIntervalMs", ConfigField.INTEGER, 10000);

    private static final ChunkUnloaderConfig INSTANCE = new ChunkUnloaderConfig();

    public ChunkUnloaderConfig() {
        register(
                ENABLED,
                UNLOAD_DISTANCE_OFFSET,
                UNLOAD_DELAY_SECONDS,
                MAX_UNLOADS_PER_RUN,
                MIN_LOADED_CHUNKS,
                CHECK_INTERVAL_MS);
    }

    public static ChunkUnloaderConfig get() {
        return INSTANCE;
    }
}
