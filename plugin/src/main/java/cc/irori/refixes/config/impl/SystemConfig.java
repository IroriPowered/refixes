package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class SystemConfig extends Configuration<SystemConfig> {

    public static final ConfigurationKey<SystemConfig, Boolean> RESPAWN_BLOCK =
            new ConfigurationKey<>("RespawnBlock", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<SystemConfig, Boolean> PROCESSING_BENCH =
            new ConfigurationKey<>("ProcessingBench", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<SystemConfig, Boolean> CRAFTING_MANAGER =
            new ConfigurationKey<>("CraftingManager", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<SystemConfig, Boolean> INTERACTION_MANAGER =
            new ConfigurationKey<>("InteractionManager", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<SystemConfig, Boolean> ENTITY_DESPAWN_TIMER =
            new ConfigurationKey<>("EntityDespawnTimer", ConfigField.BOOLEAN, true);
    private static final ConfigurationKey<SystemConfig, EntityDespawnTimerConfig> ENTITY_DESPAWN_TIMER_CONFIG =
            ConfigurationKey.subConfig("EntityDespawnTimerConfig", EntityDespawnTimerConfig.get());

    private static final SystemConfig INSTANCE = new SystemConfig();

    public SystemConfig() {
        register(
                RESPAWN_BLOCK,
                PROCESSING_BENCH,
                CRAFTING_MANAGER,
                INTERACTION_MANAGER,
                ENTITY_DESPAWN_TIMER,
                ENTITY_DESPAWN_TIMER_CONFIG);
    }

    public static SystemConfig get() {
        return INSTANCE;
    }
}
