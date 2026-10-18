package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;

public class TuningConfig extends Configuration<TuningConfig> {

    public static final ConfigurationKey<TuningConfig, PerPlayerHotRadiusConfig> PER_PLAYER_RADIUS_CONFIG =
            ConfigurationKey.subConfig("PerPlayerHotRadius", PerPlayerHotRadiusConfig.get());
    public static final ConfigurationKey<TuningConfig, IdlePlayerHandlerConfig> IDLE_PLAYER_HANDLER_CONFIG =
            ConfigurationKey.subConfig("IdlePlayerHandler", IdlePlayerHandlerConfig.get());
    public static final ConfigurationKey<TuningConfig, AiTickThrottlerConfig> AI_TICK_THROTTLER_CONFIG =
            ConfigurationKey.subConfig("AiTickThrottler", AiTickThrottlerConfig.get());
    public static final ConfigurationKey<TuningConfig, IdleWorldPauseConfig> IDLE_WORLD_PAUSE_CONFIG =
            ConfigurationKey.subConfig("IdleWorldPause", IdleWorldPauseConfig.get());
    public static final ConfigurationKey<TuningConfig, EntityDespawnTimerConfig> ENTITY_DESPAWN_TIMER_CONFIG =
            ConfigurationKey.subConfig("EntityDespawnTimer", EntityDespawnTimerConfig.get());

    private static final TuningConfig INSTANCE = new TuningConfig();

    public TuningConfig() {
        register(
                PER_PLAYER_RADIUS_CONFIG,
                IDLE_PLAYER_HANDLER_CONFIG,
                AI_TICK_THROTTLER_CONFIG,
                IDLE_WORLD_PAUSE_CONFIG,
                ENTITY_DESPAWN_TIMER_CONFIG);
    }

    public static TuningConfig get() {
        return INSTANCE;
    }
}
