package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.CubicTransfer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.TeleportSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TeleportSystems.MoveSystem.class)
public class MixinCubicEntityTransfer {

    @WrapMethod(method = "lambda$onComponentAdded$0")
    private static void refixes$scopeMove(
            Ref<EntityStore> ref,
            World target,
            CompletableFuture<Void> future,
            Store<EntityStore> store,
            Operation<Void> original) {
        Object previous = CubicTransfer.enterMove();
        try {
            original.call(ref, target, future, store);
        } finally {
            CubicTransfer.exitMove(previous);
        }
    }

    @WrapOperation(
            method = "lambda$onComponentAdded$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;removeEntity(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/RemoveReason;)Lcom/hypixel/hytale/component/Holder;"),
            require = 1,
            allow = 1)
    private static Holder<EntityStore> refixes$captureSourceSection(
            Store<EntityStore> store,
            Ref<EntityStore> ref,
            RemoveReason reason,
            Operation<Holder<EntityStore>> original,
            @Share("section") LocalRef<Ref<ChunkStore>> source,
            @Share("uuid") LocalRef<UUID> moved,
            @Share("chain") LocalRef<List<CubicTransfer.Source>> chain,
            @Share("origin") LocalRef<Ref<ChunkStore>> origin) {
        ChunkStore chunks = store.getExternalData().getWorld().getChunkStore();
        CubicTransfer.leaving(null, List.of());
        if (ref.isValid()) {
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
            Ref<ChunkStore> section = transform == null ? null : transform.getSectionRef();
            if (uuid != null && section != null && section.isValid() && section.getStore() == chunks.getStore()) {
                boolean cubic = chunks.getSaver() instanceof IChunkSaver.Cubic;
                List<CubicTransfer.Source> sources =
                        CubicTransfer.chain(chunks.getStore(), section, ref, uuid.getUuid());
                CubicTransfer.leaving(uuid.getUuid(), sources);
                if (cubic || !sources.isEmpty()) {
                    source.set(cubic ? section : null);
                    moved.set(uuid.getUuid());
                    chain.set(sources);
                }
                if (cubic) CubicTransfer.fence(chunks.getStore(), section, uuid.getUuid());
                else if (!store.getExternalData().getWorld().isAlive()) {
                    origin.set(section);
                    moved.set(uuid.getUuid());
                    if (sources.isEmpty()) chain.set(sources);
                }
            }
        }
        return original.call(store, ref, reason);
    }

    @WrapOperation(
            method = "lambda$onComponentAdded$0",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/World;execute(Ljava/lang/Runnable;)V"),
            require = 1,
            allow = 1)
    private static void refixes$releaseSourceSection(
            World target,
            Runnable arrival,
            Operation<Void> original,
            @Local(argsOnly = true) Store<EntityStore> store,
            @Share("section") LocalRef<Ref<ChunkStore>> source,
            @Share("uuid") LocalRef<UUID> moved,
            @Share("chain") LocalRef<List<CubicTransfer.Source>> chain,
            @Share("origin") LocalRef<Ref<ChunkStore>> origin) {
        UUID id = moved.get();
        if (id == null) {
            original.call(target, arrival);
            return;
        }
        World world = store.getExternalData().getWorld();
        Ref<ChunkStore> leaving = origin.get();
        CubicTransfer.Departure departure =
                leaving == null || target == world ? null : CubicTransfer.depart(world, target, leaving, id);
        Ref<ChunkStore> section = departure == null ? source.get() : leaving;
        List<CubicTransfer.Source> chained = chain.get();
        original.call(target, (Runnable) () -> {
            arrival.run();
            CubicTransfer.arrived(target, id, world, section, chained, departure);
        });
    }
}
