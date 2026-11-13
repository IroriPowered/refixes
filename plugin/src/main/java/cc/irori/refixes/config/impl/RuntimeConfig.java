package cc.irori.refixes.config.impl;

import cc.irori.refixes.config.Configuration;
import cc.irori.refixes.config.ConfigurationKey;

public class RuntimeConfig extends Configuration<RuntimeConfig> {

    public static final ConfigurationKey<RuntimeConfig, CorrectnessConfig> CORRECTNESS_CONFIG =
            ConfigurationKey.subConfig("Correctness", CorrectnessConfig.get());
    public static final ConfigurationKey<RuntimeConfig, TuningConfig> TUNING_CONFIG =
            ConfigurationKey.subConfig("Tuning", TuningConfig.get());
    public static final ConfigurationKey<RuntimeConfig, FeaturesConfig> FEATURES_CONFIG =
            ConfigurationKey.subConfig("Features", FeaturesConfig.get());
    public static final ConfigurationKey<RuntimeConfig, RecoveryConfig> RECOVERY_CONFIG =
            ConfigurationKey.subConfig("Recovery", RecoveryConfig.get());
    public static final ConfigurationKey<RuntimeConfig, CompatibilityConfig> COMPATIBILITY_CONFIG =
            ConfigurationKey.subConfig("Compatibility", CompatibilityConfig.get());
    public static final ConfigurationKey<RuntimeConfig, DiagnosticsConfig> DIAGNOSTICS_CONFIG =
            ConfigurationKey.subConfig("Diagnostics", DiagnosticsConfig.get());

    private static final RuntimeConfig INSTANCE = new RuntimeConfig();

    public RuntimeConfig() {
        register(
                CORRECTNESS_CONFIG,
                TUNING_CONFIG,
                FEATURES_CONFIG,
                RECOVERY_CONFIG,
                COMPATIBILITY_CONFIG,
                DIAGNOSTICS_CONFIG);
    }

    public static RuntimeConfig get() {
        return INSTANCE;
    }
}
