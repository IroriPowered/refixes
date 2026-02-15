package cc.irori.refixes.config.field;

import com.hypixel.hytale.codec.Codec;

record SimpleConfigField<T>(Codec<T> codec) implements ConfigField<T> {

    @Override
    public T valueForRead(T value) {
        return value;
    }

    @Override
    public T valueForStore(T value) {
        return value;
    }
}
