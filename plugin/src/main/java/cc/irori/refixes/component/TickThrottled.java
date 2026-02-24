package cc.irori.refixes.component;

import cc.irori.refixes.Refixes;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.Nullable;

public class TickThrottled implements Component<EntityStore> {

    public static final BuilderCodec<TickThrottled> CODEC =
            BuilderCodec.builder(TickThrottled.class, TickThrottled::new).build();

    private static final TickThrottled INSTANCE = new TickThrottled();

    private TickThrottled() {}

    public static ComponentType<EntityStore, TickThrottled> getComponentType() {
        return Refixes.get().getTickThrottledComponent();
    }

    public static TickThrottled get() {
        return INSTANCE;
    }

    @Override
    public @Nullable Component<EntityStore> clone() {
        return get();
    }
}
