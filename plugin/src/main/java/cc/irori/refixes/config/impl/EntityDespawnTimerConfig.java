package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class EntityDespawnTimerConfig extends Configuration<EntityDespawnTimerConfig> {

    public static final ConfigurationKey<EntityDespawnTimerConfig, Integer> ITEM_DESPAWN_SECONDS =
            new ConfigurationKey<>("ItemDespawnSeconds", ConfigField.INTEGER, 300);
    public static final ConfigurationKey<EntityDespawnTimerConfig, Integer> BLOCK_ENTITY_DESPAWN_SECONDS =
            new ConfigurationKey<>("BlockEntityDespawnSeconds", ConfigField.INTEGER, 300);
    public static final ConfigurationKey<EntityDespawnTimerConfig, Integer> PROJECTILE_DESPAWN_SECONDS =
            new ConfigurationKey<>("ProjectileDespawnSeconds", ConfigField.INTEGER, 60);
    public static final ConfigurationKey<EntityDespawnTimerConfig, Boolean> ADD_TIMER_WHEN_MISSING =
            new ConfigurationKey<>("AddTimerWhenMissing", ConfigField.BOOLEAN, false);
    public static final ConfigurationKey<EntityDespawnTimerConfig, Boolean> OVERRIDE_EXISTING_TIMERS =
            new ConfigurationKey<>("OverrideExistingTimers", ConfigField.BOOLEAN, false);

    private static final EntityDespawnTimerConfig INSTANCE = new EntityDespawnTimerConfig();

    public EntityDespawnTimerConfig() {
        register(
                ITEM_DESPAWN_SECONDS,
                BLOCK_ENTITY_DESPAWN_SECONDS,
                PROJECTILE_DESPAWN_SECONDS,
                ADD_TIMER_WHEN_MISSING,
                OVERRIDE_EXISTING_TIMERS);
    }

    public static EntityDespawnTimerConfig get() {
        return INSTANCE;
    }

    public boolean shouldRegisterSystem() {
        return getValue(ADD_TIMER_WHEN_MISSING) || getValue(OVERRIDE_EXISTING_TIMERS);
    }
}
