package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class AiTickThrottlerConfig extends Configuration<AiTickThrottlerConfig> {

    public static final ConfigurationKey<AiTickThrottlerConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<AiTickThrottlerConfig, Boolean> CLEANUP_FROZEN_ENTITIES =
            new ConfigurationKey<>("CleanupFrozenEntities", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<AiTickThrottlerConfig, Integer> UPDATE_INTERVAL_MS =
            new ConfigurationKey<>("UpdateIntervalMs", ConfigField.INTEGER, 150);

    // NPCs within this chunk distance get full tick rate
    public static final ConfigurationKey<AiTickThrottlerConfig, Integer> NEAR_CHUNKS =
            new ConfigurationKey<>("NearChunks", ConfigField.INTEGER, 1);
    // NPCs within this chunk distance get mid tick rate
    public static final ConfigurationKey<AiTickThrottlerConfig, Integer> MID_CHUNKS =
            new ConfigurationKey<>("MidChunks", ConfigField.INTEGER, 2);
    // NPCs within this chunk distance get far tick rate
    public static final ConfigurationKey<AiTickThrottlerConfig, Integer> FAR_CHUNKS =
            new ConfigurationKey<>("FarChunks", ConfigField.INTEGER, 4);

    public static final ConfigurationKey<AiTickThrottlerConfig, Float> MID_TICK_SECONDS =
            new ConfigurationKey<>("MidTickSeconds", ConfigField.FLOAT, 0.2f);
    public static final ConfigurationKey<AiTickThrottlerConfig, Float> FAR_TICK_SECONDS =
            new ConfigurationKey<>("FarTickSeconds", ConfigField.FLOAT, 0.5f);
    public static final ConfigurationKey<AiTickThrottlerConfig, Float> VERY_FAR_TICK_SECONDS =
            new ConfigurationKey<>("VeryFarTickSeconds", ConfigField.FLOAT, 1.0f);
    public static final ConfigurationKey<AiTickThrottlerConfig, Float> MIN_TICK_SECONDS =
            new ConfigurationKey<>("MinTickSeconds", ConfigField.FLOAT, 0.05f);

    public static final ConfigurationKey<AiTickThrottlerConfig, Boolean> LEGACY_CLEANUP =
            new ConfigurationKey<>("LegacyCleanup", ConfigField.BOOLEAN, false);

    private static final AiTickThrottlerConfig INSTANCE = new AiTickThrottlerConfig();

    public AiTickThrottlerConfig() {
        register(
                ENABLED,
                CLEANUP_FROZEN_ENTITIES,
                UPDATE_INTERVAL_MS,
                NEAR_CHUNKS,
                MID_CHUNKS,
                FAR_CHUNKS,
                MID_TICK_SECONDS,
                FAR_TICK_SECONDS,
                VERY_FAR_TICK_SECONDS,
                MIN_TICK_SECONDS,
                LEGACY_CLEANUP);
    }

    public static AiTickThrottlerConfig get() {
        return INSTANCE;
    }
}
