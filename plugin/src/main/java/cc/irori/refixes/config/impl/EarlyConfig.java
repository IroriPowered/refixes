package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class EarlyConfig extends Configuration<EarlyConfig> {

    private static final ConfigurationKey<EarlyConfig, CylinderVisibilityConfig> CYLINDER_VISIBILITY_CONFIG =
            ConfigurationKey.subConfig("CylinderVisibility", CylinderVisibilityConfig.get());
    private static final ConfigurationKey<EarlyConfig, KDTreeOptimizationConfig> KDTREE_OPTIMIZATION_CONFIG =
            ConfigurationKey.subConfig("KDTreeOptimization", KDTreeOptimizationConfig.get());

    public static final ConfigurationKey<EarlyConfig, Integer> MAX_SECTIONS_PER_SECOND =
            new ConfigurationKey<>("MaxSectionsPerSecond", ConfigField.INTEGER, 360);
    public static final ConfigurationKey<EarlyConfig, Integer> MAX_SECTIONS_PER_TICK =
            new ConfigurationKey<>("MaxSectionsPerTick", ConfigField.INTEGER, 40);
    public static final ConfigurationKey<EarlyConfig, Boolean> VANILLA_KEEP_SPAWN_LOADED =
            new ConfigurationKey<>("VanillaKeepSpawnLoaded", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<EarlyConfig, Integer> UNLOAD_DISTANCE_OFFSET =
            new ConfigurationKey<>("UnloadDistanceOffset", ConfigField.INTEGER, 4);

    public static final ConfigurationKey<EarlyConfig, Integer> PATHFINDING_MAX_PATH_LENGTH =
            new ConfigurationKey<>("PathfindingMaxPathLength", ConfigField.INTEGER, 200);
    public static final ConfigurationKey<EarlyConfig, Integer> PATHFINDING_OPEN_NODES_LIMIT =
            new ConfigurationKey<>("PathfindingOpenNodesLimit", ConfigField.INTEGER, 80);
    public static final ConfigurationKey<EarlyConfig, Integer> PATHFINDING_TOTAL_NODES_LIMIT =
            new ConfigurationKey<>("PathfindingTotalNodesLimit", ConfigField.INTEGER, 400);
    public static final ConfigurationKey<EarlyConfig, Integer> PATHFINDING_MAX_NEW_SEARCHES_PER_TICK =
            new ConfigurationKey<>("PathfindingMaxNewSearchesPerTick", ConfigField.INTEGER, 8);
    public static final ConfigurationKey<EarlyConfig, Integer> PATHFINDING_MAX_NODE_EXPANSIONS_PER_TICK =
            new ConfigurationKey<>("PathfindingMaxNodeExpansionsPerTick", ConfigField.INTEGER, 600);

    public static final ConfigurationKey<EarlyConfig, Integer> SHUTDOWN_SAVE_TIMEOUT_SECONDS =
            new ConfigurationKey<>("ShutdownSaveTimeoutSeconds", ConfigField.INTEGER, 10);

    public static final ConfigurationKey<EarlyConfig, Integer> BACKPRESSURE_MAX_OUTBOUND_BYTES =
            new ConfigurationKey<>("BackpressureMaxOutboundBytes", ConfigField.INTEGER, 16777216);
    public static final ConfigurationKey<EarlyConfig, Integer> BACKPRESSURE_GRACE_MS =
            new ConfigurationKey<>("BackpressureGraceMs", ConfigField.INTEGER, 10000);

    private static final EarlyConfig INSTANCE = new EarlyConfig();

    public EarlyConfig() {
        register(
                CYLINDER_VISIBILITY_CONFIG,
                KDTREE_OPTIMIZATION_CONFIG,
                MAX_SECTIONS_PER_SECOND,
                MAX_SECTIONS_PER_TICK,
                VANILLA_KEEP_SPAWN_LOADED,
                UNLOAD_DISTANCE_OFFSET,
                PATHFINDING_MAX_PATH_LENGTH,
                PATHFINDING_OPEN_NODES_LIMIT,
                PATHFINDING_TOTAL_NODES_LIMIT,
                PATHFINDING_MAX_NEW_SEARCHES_PER_TICK,
                PATHFINDING_MAX_NODE_EXPANSIONS_PER_TICK,
                SHUTDOWN_SAVE_TIMEOUT_SECONDS,
                BACKPRESSURE_MAX_OUTBOUND_BYTES,
                BACKPRESSURE_GRACE_MS);
    }

    public static EarlyConfig get() {
        return INSTANCE;
    }
}
