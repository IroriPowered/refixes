package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityStore.UUIDSystem.class)
public class MixinUUIDSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Inject(
            method = "onEntityRemove",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hypixel/hytale/component/CommandBuffer;getComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)Lcom/hypixel/hytale/component/Component;",
                    shift = At.Shift.BY,
                    by = 3
            ),
            cancellable = true
    )
    private void refixes$validateUuidComponent(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer, CallbackInfo ci, @Local(name = "uuidComponent") UUIDComponent uuidComponent) {
        if (uuidComponent == null) {
            refixes$LOGGER.atWarning().log("UUIDSystem#onEntityRemove(): UUIDComponent is null");
            ci.cancel();
        }
    }
}
