package cc.irori.refixes.component;

import cc.irori.refixes.Refixes;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.StepComponent;
import javax.annotation.Nullable;

public class TickThrottled implements Component<EntityStore> {

    public static final BuilderCodec<TickThrottled> CODEC =
            BuilderCodec.builder(TickThrottled.class, TickThrottled::new).build();

    private static final TickThrottled INSTANCE = new TickThrottled();

    private static final TickThrottled RELEASING = new TickThrottled();

    private final StepComponent pendingStep;

    private TickThrottled() {
        this(null);
    }

    private TickThrottled(StepComponent pendingStep) {
        this.pendingStep = pendingStep;
    }

    public static ComponentType<EntityStore, TickThrottled> getComponentType() {
        return Refixes.get().getTickThrottledComponent();
    }

    public static TickThrottled get() {
        return INSTANCE;
    }

    public static TickThrottled withPendingStep(StepComponent step) {
        return new TickThrottled(step);
    }

    public static TickThrottled releasing() {
        return RELEASING;
    }

    public boolean isReleasing() {
        return this == RELEASING;
    }

    public boolean isLive() {
        return this == INSTANCE || this == RELEASING || pendingStep != null;
    }

    public @Nullable StepComponent getPendingStep() {
        return pendingStep;
    }

    @Override
    public @Nullable Component<EntityStore> clone() {
        return this;
    }
}
