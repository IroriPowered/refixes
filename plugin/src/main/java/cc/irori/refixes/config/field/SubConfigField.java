package cc.irori.refixes.config.field;

import cc.irori.refixes.config.Configuration;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public record SubConfigField<T extends Configuration<?>>(BuilderCodec<T> codec) implements ConfigField<T> {

    @Override
    public T valueForRead(T value) {
        return value;
    }

    @Override
    public T valueForStore(T value) {
        return value;
    }
}
