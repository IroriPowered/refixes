package cc.irori.refixes.config.field;

import com.hypixel.hytale.codec.Codec;

public interface ConfigField<T> {

    ConfigField<Boolean> BOOLEAN = new SimpleConfigField<>(Codec.BOOLEAN);

    T valueForRead(T value);

    T valueForStore(T value);

    Codec<T> codec();
}
