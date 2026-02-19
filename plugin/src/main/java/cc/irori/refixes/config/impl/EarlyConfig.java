package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class EarlyConfig extends Configuration<EarlyConfig> {

    private static final ConfigurationKey<EarlyConfig, TickSleepOptimizationConfig> TICK_SLEEP_OPTIMIZATION_CONFIG =
            ConfigurationKey.subConfig("TickSleepOptimization", TickSleepOptimizationConfig.get());
    private static final ConfigurationKey<EarlyConfig, CylinderVisibilityConfig> CYLINDER_VISIBILITY_CONFIG =
            ConfigurationKey.subConfig("CylinderVisibility", CylinderVisibilityConfig.get());
    private static final ConfigurationKey<EarlyConfig, KDTreeOptimizationConfig> KDTREE_OPTIMIZATION_CONFIG =
            ConfigurationKey.subConfig("KDTreeOptimization", KDTreeOptimizationConfig.get());

    public static final ConfigurationKey<EarlyConfig, Boolean> DISABLE_FLUID_PRE_PROCESS =
            new ConfigurationKey<>("DisableFluidPreProcess", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<EarlyConfig, Boolean> ASYNC_BLOCK_PRE_PROCESS =
            new ConfigurationKey<>("AsyncBlockPreProcess", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<EarlyConfig, Integer> MAX_CHUNKS_PER_SECOND =
            new ConfigurationKey<>("MaxChunksPerSecond", ConfigField.INTEGER, 36);
    public static final ConfigurationKey<EarlyConfig, Integer> MAX_CHUNKS_PER_TICK =
            new ConfigurationKey<>("MaxChunksPerTick", ConfigField.INTEGER, 4);

    private static final EarlyConfig INSTANCE = new EarlyConfig();

    public EarlyConfig() {
        register(
                TICK_SLEEP_OPTIMIZATION_CONFIG,
                CYLINDER_VISIBILITY_CONFIG,
                KDTREE_OPTIMIZATION_CONFIG,
                DISABLE_FLUID_PRE_PROCESS,
                ASYNC_BLOCK_PRE_PROCESS,
                MAX_CHUNKS_PER_SECOND,
                MAX_CHUNKS_PER_TICK);
    }

    public static EarlyConfig get() {
        return INSTANCE;
    }
}
