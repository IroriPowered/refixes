package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.NPCActivationRecovery;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = EntitySection.EntitySectionLoadingSystem.class, priority = 1100)
public abstract class MixinNPCSectionActivation {
    @WrapOperation(
            method =
                    "onComponentRemoved(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/NonTicking;Lcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/component/CommandBuffer;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;addEntities([Lcom/hypixel/hytale/component/Holder;IILcom/hypixel/hytale/component/AddReason;)[Lcom/hypixel/hytale/component/Ref;"),
            require = 1,
            allow = 1)
    private Ref<EntityStore>[] refixes$loadSavedNPCs(
            Store<EntityStore> store,
            Holder<EntityStore>[] holders,
            int start,
            int length,
            AddReason reason,
            Operation<Ref<EntityStore>[]> original,
            @Local EntitySection section) {
        EntitySection previous = NPCActivationRecovery.section(section);
        try {
            return original.call(store, holders, start, length, reason);
        } finally {
            NPCActivationRecovery.section(previous);
        }
    }
}
