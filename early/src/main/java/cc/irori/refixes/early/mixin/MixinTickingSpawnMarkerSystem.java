package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.SpawnReferenceSystems;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fixes "java.lang.IllegalStateException: Incorrect store for entity reference" when running CommandBuffer.getComponent

@Mixin(SpawnReferenceSystems.TickingSpawnMarkerSystem.class)
public class MixinTickingSpawnMarkerSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<IllegalStateException> refixes$EXCEPTION = new ThreadLocal<>();

    @Redirect(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/CommandBuffer;getComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)Lcom/hypixel/hytale/component/Component;"))
    private <T extends Component<EntityStore>> T refixes$safelyGetComponent(
            CommandBuffer<EntityStore> instance, Ref<EntityStore> ref, ComponentType<EntityStore, T> componentType) {
        try {
            return instance.getComponent(ref, componentType);
        } catch (IllegalStateException e) {
            refixes$EXCEPTION.set(e);
            return null;
        }
    }

    @Inject(
            method = "tick",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/CommandBuffer;getComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)Lcom/hypixel/hytale/component/Component;",
                            shift = At.Shift.AFTER),
            cancellable = true)
    private void refixes$discardOnNullComponent(
            float dt,
            int index,
            ArchetypeChunk<EntityStore> archetypeChunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci,
            @Local(name = "npcComponent") NPCEntity npcComponent) {
        IllegalStateException e = refixes$EXCEPTION.get();
        refixes$EXCEPTION.remove();

        if (e != null) {
            npcComponent.setToDespawn();
            refixes$LOGGER.atWarning().withCause(e).log(
                    "TickingSpawnMarkerSystem#tick(): NPCEntity despawning due to IllegalStateException in getComponent");
            ci.cancel();
        }
    }
}
