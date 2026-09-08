package cc.irori.refixes.early.util;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.function.consumer.TriConsumer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.PlayerUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ParallelAnimationDispatch {

    private ParallelAnimationDispatch() {}

    @SuppressWarnings("unchecked")
    public static void forEachViewer(
            Ref<EntityStore> ref,
            TriConsumer<Ref<EntityStore>, PlayerRef, ComponentAccessor<EntityStore>> consumer,
            Ref<EntityStore> ignoredPlayerRef,
            ComponentAccessor<EntityStore> accessor) {
        Store<EntityStore> store = accessor.getExternalData().getStore();
        if (store.isInThread()) {
            PlayerUtil.forEachPlayerThatCanSeeEntity(ref, consumer, ignoredPlayerRef, accessor);
            return;
        }
        CommandBuffer<?> origin = ParallelStoreContext.commandBuffer();
        if (origin == null || origin.getStore() != store || !store.isProcessing() || ref.getStore() != store) {
            throw new IllegalStateException("Animation dispatch requires the active entity Store task");
        }
        ((CommandBuffer<EntityStore>) origin).run(owner -> {
            if (ref.isValid() && ref.getStore() == owner) {
                PlayerUtil.forEachPlayerThatCanSeeEntity(ref, consumer, ignoredPlayerRef, owner);
            }
        });
    }
}
