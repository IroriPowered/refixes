package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;

public class ServiceConfig extends Configuration<ServiceConfig> {

    private static final ConfigurationKey<ServiceConfig, ChunkUnloaderConfig> CHUNK_UNLOADER_CONFIG =
            ConfigurationKey.subConfig("ChunkUnloader", ChunkUnloaderConfig.get());
    private static final ConfigurationKey<ServiceConfig, PerPlayerHotRadiusConfig> PER_PLAYER_RADIUS_CONFIG =
            ConfigurationKey.subConfig("PerPlayerHotRadius", PerPlayerHotRadiusConfig.get());
    private static final ConfigurationKey<ServiceConfig, IdlePlayerHandlerConfig> IDLE_PLAYER_HANDLER_CONFIG =
            ConfigurationKey.subConfig("IdlePlayerHandler", IdlePlayerHandlerConfig.get());
    private static final ConfigurationKey<ServiceConfig, AiTickThrottlerConfig> AI_TICK_THROTTLER_CONFIG =
            ConfigurationKey.subConfig("AiTickThrottler", AiTickThrottlerConfig.get());

    private static final ServiceConfig INSTANCE = new ServiceConfig();

    public ServiceConfig() {
        register(
                CHUNK_UNLOADER_CONFIG,
                PER_PLAYER_RADIUS_CONFIG,
                IDLE_PLAYER_HANDLER_CONFIG,
                AI_TICK_THROTTLER_CONFIG);
    }

    public static ServiceConfig get() {
        return INSTANCE;
    }
}
