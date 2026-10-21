package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.data.unknown.UnknownComponents;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ThrottleOwnership {

    public static final String MARKER_ID = "Refixes_TickThrottled";

    private ThrottleOwnership() {}

    public static void claim(Store<EntityStore> store, Ref<EntityStore> ref, ComponentType<?, ?> type) {
        if (type != Frozen.getComponentType() || !ref.isValid()) {
            return;
        }
        ComponentType<EntityStore, ?> marker = store.getRegistry().getData().getComponentType(MARKER_ID);
        if (marker != null) {
            store.tryRemoveComponent(ref, marker);
            return;
        }
        ComponentType<EntityStore, UnknownComponents<EntityStore>> unknownType =
                store.getRegistry().getUnknownComponentType();
        if (store.getArchetype(ref).contains(unknownType)) {
            store.getComponent(ref, unknownType).getUnknownComponents().remove(MARKER_ID);
        }
    }

    public static void claim(CommandBuffer<EntityStore> commandBuffer, Ref<EntityStore> ref, ComponentType<?, ?> type) {
        if (type == Frozen.getComponentType()) {
            commandBuffer.run(store -> claim(store, ref, type));
        }
    }
}
