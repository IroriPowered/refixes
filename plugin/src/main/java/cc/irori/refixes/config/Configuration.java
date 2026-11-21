package cc.irori.refixes.config;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class Configuration<C extends Configuration<?>> {

    private final Map<ConfigurationKey<?, Object>, Object> values = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    protected final <T> void register(ConfigurationKey<C, T> key) {
        values.put((ConfigurationKey<?, Object>) key, key.defaultValue());
    }

    @SafeVarargs
    protected final void register(ConfigurationKey<C, ?>... keys) {
        for (ConfigurationKey<C, ?> key : keys) {
            register(key);
        }
    }

    @SuppressWarnings("unchecked")
    public final BuilderCodec<C> getCodec() {
        BuilderCodec.Builder<C> builder = BuilderCodec.builder((Class<C>) getClass(), () -> (C) this);
        for (ConfigurationKey<?, Object> key : values.keySet()) {
            builder.append(
                            new KeyedCodec<>(key.name(), key.field().codec()),
                            (config, aValue, extraInfo) ->
                                    values.put(key, key.field().valueForRead(aValue)),
                            (config, extraInfo) -> key.field().valueForStore(values.get(key)))
                    .add();
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    public final <T> T getValue(ConfigurationKey<C, T> key) {
        if (!values.containsKey(key)) {
            throw new IllegalArgumentException("Configuration does not contain key: " + key.name());
        }
        return (T) values.get(key);
    }

    @SuppressWarnings("unchecked")
    public final <T> void setValue(ConfigurationKey<C, T> key, T value) {
        values.put((ConfigurationKey<?, Object>) key, key.field().valueForStore(value));
    }

    public final void collectChangedValues(String prefix, List<String> out) {
        for (Map.Entry<ConfigurationKey<?, Object>, Object> entry : values.entrySet()) {
            String path = prefix + entry.getKey().name();
            Object value = entry.getValue();
            if (value instanceof Configuration<?> child) {
                child.collectChangedValues(path + ".", out);
            } else if (!Objects.deepEquals(value, entry.getKey().defaultValue())) {
                out.add(path + "=" + (value instanceof Object[] array ? Arrays.toString(array) : value));
            }
        }
    }
}
