package cc.irori.refixes.config.field;

import com.hypixel.hytale.codec.Codec;

public interface ConfigField<T> {

    ConfigField<Boolean> BOOLEAN = new SimpleConfigField<>(Codec.BOOLEAN);
    ConfigField<Integer> INTEGER = new SimpleConfigField<>(Codec.INTEGER);
    ConfigField<Float> FLOAT = new SimpleConfigField<>(Codec.FLOAT);
    ConfigField<String> STRING = new SimpleConfigField<>(Codec.STRING);
    ConfigField<String[]> STRING_ARRAY = new SimpleConfigField<>(Codec.STRING_ARRAY);

    T valueForRead(T value);

    T valueForStore(T value);

    Codec<T> codec();
}
