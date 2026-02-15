package cc.irori.refixes.config;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.HashMap;
import java.util.Map;

public abstract class Configuration {

    private final Map<ConfigurationKey<?, Object>, Object> values = new HashMap<>();

    @SuppressWarnings("unchecked")
    protected final <C extends Configuration, T> void register(ConfigurationKey<C, T> key) {
        values.put((ConfigurationKey<?, Object>) key, key.defaultValue());
    }

    @SafeVarargs
    protected final <C extends Configuration> void register(ConfigurationKey<C, ?>... keys) {
        for (ConfigurationKey<C, ?> key : keys) {
            register(key);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Configuration> BuilderCodec<T> getCodec() {
        BuilderCodec.Builder<T> builder = BuilderCodec.builder((Class<T>) getClass(), () -> (T) this);
        for (Map.Entry<ConfigurationKey<?, Object>, Object> entry : values.entrySet()) {
            ConfigurationKey<?, Object> key = entry.getKey();
            Object value = entry.getValue();

            builder.append(
                            new KeyedCodec<>(key.name(), key.field().getCodec()),
                            (config, aValue, extraInfo) ->
                                    values.put(key, key.field().valueForRead(aValue)),
                            (config, extraInfo) -> key.field().valueForStore(values.get(key)))
                    .add();
        }
        return builder.build();
    }
}
