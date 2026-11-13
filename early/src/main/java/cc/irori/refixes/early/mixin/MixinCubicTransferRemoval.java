package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.CubicTransfer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.system.EntitySystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntitySystems.UnloadEntityFromChunk.class)
public class MixinCubicTransferRemoval {

    @WrapOperation(
            method = "onEntityRemove",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/EntitySection;removeEntityReference(Lcom/hypixel/hytale/component/Ref;)V"),
            require = 1,
            allow = 1)
    private void refixes$retireRemovedSources(EntitySection section, Ref<EntityStore> ref, Operation<Void> original) {
        UUIDComponent uuid = ref.isValid() ? ref.getStore().getComponent(ref, UUIDComponent.getComponentType()) : null;
        if (uuid == null) {
            original.call(section, ref);
            return;
        }
        List<CubicTransfer.Source> sources = CubicTransfer.removing(section, ref);
        World world = ref.getStore().getExternalData().getWorld();
        original.call(section, ref);
        CubicTransfer.removed(world, uuid.getUuid(), sources);
    }
}
