package cc.irori.refixes.config;

import cc.irori.refixes.config.field.ConfigField;
import cc.irori.refixes.config.field.SubConfigField;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public record ConfigurationKey<C extends Configuration<?>, T>(String name, ConfigField<T> field, T defaultValue) {

    @SuppressWarnings("unchecked")
    public static <C extends Configuration<?>, T extends Configuration<?>> ConfigurationKey<C, T> subConfig(
            String name, T configuration) {
        return new ConfigurationKey<>(
                name, new SubConfigField<>((BuilderCodec<T>) configuration.getCodec()), configuration);
    }
}
