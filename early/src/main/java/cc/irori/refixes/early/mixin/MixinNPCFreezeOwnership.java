package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.ThrottleOwnership;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.commands.NPCFreezeCommand;
import com.hypixel.hytale.server.npc.commands.NPCStepCommand;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({NPCFreezeCommand.class, NPCStepCommand.class})
public class MixinNPCFreezeOwnership {

    @WrapOperation(
            method =
                    "execute(Lcom/hypixel/hytale/server/core/command/system/CommandContext;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/component/Store;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;ensureComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)V"),
            require = 1)
    private void refixes$claimTargetFreeze(
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            ComponentType<EntityStore, ?> type,
            Operation<Void> original) {
        original.call(store, ref, type);
        ThrottleOwnership.claim(store, ref, type);
    }

    @WrapOperation(
            method = {
                "lambda$execute$0(ILcom/hypixel/hytale/component/ArchetypeChunk;Lcom/hypixel/hytale/component/CommandBuffer;)V",
                "lambda$execute$0(FILcom/hypixel/hytale/component/ArchetypeChunk;Lcom/hypixel/hytale/component/CommandBuffer;)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/CommandBuffer;ensureComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)V"),
            require = 1)
    private static void refixes$claimAllFreeze(
            CommandBuffer<EntityStore> commandBuffer,
            Ref<EntityStore> ref,
            ComponentType<EntityStore, ?> type,
            Operation<Void> original) {
        original.call(commandBuffer, ref, type);
        ThrottleOwnership.claim(commandBuffer, ref, type);
    }
}
