package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class CorrectnessConfig extends Configuration<CorrectnessConfig> {

    public static final ConfigurationKey<CorrectnessConfig, Boolean> CRAFTING_MANAGER =
            new ConfigurationKey<>("CraftingManager", ConfigField.BOOLEAN, true);

    private static final CorrectnessConfig INSTANCE = new CorrectnessConfig();

    public CorrectnessConfig() {
        register(CRAFTING_MANAGER);
    }

    public static CorrectnessConfig get() {
        return INSTANCE;
    }
}
