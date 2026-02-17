package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;
import cc.irori.refixes.config.field.ConfigField;

public class CylinderVisibilityConfig extends Configuration<CylinderVisibilityConfig> {

    public static final ConfigurationKey<CylinderVisibilityConfig, Boolean> ENABLED =
            new ConfigurationKey<>("Enabled", ConfigField.BOOLEAN, true);
    public static final ConfigurationKey<CylinderVisibilityConfig, Double> HEIGHT_MULTIPLIER =
            new ConfigurationKey<>("HeightMultiplier", ConfigField.DOUBLE, 2.0);

    private static final CylinderVisibilityConfig INSTANCE = new CylinderVisibilityConfig();

    public CylinderVisibilityConfig() {
        register(ENABLED, HEIGHT_MULTIPLIER);
    }

    public static CylinderVisibilityConfig get() {
        return INSTANCE;
    }
}
