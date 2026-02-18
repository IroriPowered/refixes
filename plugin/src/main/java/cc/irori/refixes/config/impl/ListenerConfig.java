package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class ListenerConfig extends Configuration<ListenerConfig> {

    public static final ConfigurationKey<ListenerConfig, Boolean> INSTANCE_POSITION_TRACKER =
            new ConfigurationKey<>("InstancePositionTracker", ConfigField.BOOLEAN, true);

    private static final ListenerConfig INSTANCE = new ListenerConfig();

    public ListenerConfig() {
        register(INSTANCE_POSITION_TRACKER);
    }

    public static ListenerConfig get() {
        return INSTANCE;
    }
}
