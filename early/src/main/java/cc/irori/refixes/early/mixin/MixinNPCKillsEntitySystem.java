package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.systems.NPCDeathSystems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NPCDeathSystems.NPCKillsEntitySystem.class)
public class MixinNPCKillsEntitySystem {

    @WrapOperation(
            method =
                    "onComponentAdded(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/modules/entity/damage/DeathComponent;Lcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/component/CommandBuffer;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;getComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)Lcom/hypixel/hytale/component/Component;",
                            ordinal = 0))
    private <T extends Component<EntityStore>> T refixes$guardKillerRef(
            Store<EntityStore> store, Ref<EntityStore> ref, ComponentType<EntityStore, T> type, Operation<T> original) {
        if (!ref.isValid() || ref.getStore() != store) {
            return null;
        }
        return original.call(store, ref, type);
    }
}
