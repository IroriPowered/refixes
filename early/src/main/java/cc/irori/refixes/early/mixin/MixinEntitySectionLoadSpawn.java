package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.EntitySectionLoad;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.UpdateLocationSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UpdateLocationSystems.SpawnSystem.class)
public class MixinEntitySectionLoadSpawn {

    @Inject(
            method =
                    "onEntityAdded(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/AddReason;Lcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/component/CommandBuffer;)V",
            at = @At("HEAD"),
            require = 1,
            allow = 1)
    private void refixes$clearSectionLoad(
            Ref<EntityStore> ref,
            AddReason reason,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci) {
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform != null) ((EntitySectionLoad) transform).refixes$setSectionLoadPending(false);
    }
}
