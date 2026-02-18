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

    private static final EarlyConfig INSTANCE = new EarlyConfig();

    public EarlyConfig() {
        register(
                TICK_SLEEP_OPTIMIZATION_CONFIG,
                CYLINDER_VISIBILITY_CONFIG,
                KDTREE_OPTIMIZATION_CONFIG,
                DISABLE_FLUID_PRE_PROCESS);
    }

    public static EarlyConfig get() {
        return INSTANCE;
    }
}
