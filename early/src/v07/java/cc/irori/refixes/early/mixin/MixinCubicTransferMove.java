package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.duck.CubicTransferSection;
import cc.irori.refixes.early.util.CubicTransfer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.system.UpdateLocationSystems;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.List;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale("<0.8.0-pre.1")
@Mixin(UpdateLocationSystems.class)
public class MixinCubicTransferMove {

    @WrapOperation(
            method = "updateEntityInChunk",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/EntitySection;removeEntityReference(Lcom/hypixel/hytale/component/Ref;)V"),
            require = 1,
            allow = 1)
    private static void refixes$captureMovedSources(
            EntitySection section,
            Ref<EntityStore> ref,
            Operation<Void> original,
            @Local(argsOnly = true, ordinal = 1) ComponentAccessor<EntityStore> entities,
            @Share("uuid") LocalRef<UUID> moved,
            @Share("chain") LocalRef<List<CubicTransfer.Source>> chain) {
        List<CubicTransfer.Pending> pending =
                section instanceof CubicTransferSection holder ? holder.refixes$peekTransfers() : null;
        if (pending != null && !pending.isEmpty()) {
            UUIDComponent uuid = entities.getComponent(ref, UUIDComponent.getComponentType());
            List<CubicTransfer.Source> sources =
                    uuid == null ? List.of() : CubicTransfer.chain(section, ref, uuid.getUuid());
            if (!sources.isEmpty()) {
                moved.set(uuid.getUuid());
                chain.set(sources);
            }
        }
        original.call(section, ref);
    }

    @WrapOperation(
            method = "updateEntityInChunk",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/EntitySection;addEntityReference(Lcom/hypixel/hytale/component/Ref;)V"),
            require = 1,
            allow = 1)
    private static void refixes$handOffMovedSources(
            EntitySection section,
            Ref<EntityStore> ref,
            Operation<Void> original,
            @Local(argsOnly = true) Dirty dirty,
            @Local(argsOnly = true, ordinal = 1) ComponentAccessor<EntityStore> entities,
            @Share("uuid") LocalRef<UUID> moved,
            @Share("chain") LocalRef<List<CubicTransfer.Source>> chain) {
        original.call(section, ref);
        List<CubicTransfer.Source> sources = chain.get();
        if (sources != null)
            CubicTransfer.moved(entities.getExternalData().getWorld(), section, ref, dirty, moved.get(), sources);
    }

    @WrapOperation(
            method = "updateEntityInChunk",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/EntitySection;addEntityHolder(Lcom/hypixel/hytale/component/Holder;)V"),
            require = 1,
            allow = 1)
    private static void refixes$handOffParkedSources(
            EntitySection section,
            Holder<EntityStore> holder,
            Operation<Void> original,
            @Local(argsOnly = true, ordinal = 1) ComponentAccessor<EntityStore> entities,
            @Share("uuid") LocalRef<UUID> moved,
            @Share("chain") LocalRef<List<CubicTransfer.Source>> chain) {
        original.call(section, holder);
        List<CubicTransfer.Source> sources = chain.get();
        if (sources != null)
            CubicTransfer.movedHolder(entities.getExternalData().getWorld(), section, holder, moved.get(), sources);
    }
}
