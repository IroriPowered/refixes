package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.EntitySectionLoad;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.UpdateLocationSystems;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.joml.Vector3d;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(UpdateLocationSystems.class)
public class MixinEntitySectionLoad {

    @Inject(
            method =
                    "updateLocation(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/modules/entity/component/TransformComponent;Lcom/hypixel/hytale/server/core/entity/Dirty;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/component/CommandBuffer;)V",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lcom/hypixel/hytale/server/core/modules/entity/system/UpdateLocationSystems;LOGGER:Lcom/hypixel/hytale/logger/HytaleLogger;",
                            opcode = Opcodes.GETSTATIC,
                            ordinal = 1),
            cancellable = true,
            require = 1,
            allow = 1)
    private static void refixes$requestSectionOnce(
            Ref<EntityStore> ref,
            TransformComponent transform,
            Dirty dirty,
            World world,
            CommandBuffer<EntityStore> commandBuffer,
            CallbackInfo ci) {
        if (commandBuffer.getArchetype(ref).contains(Player.getComponentType())) return;
        EntitySectionLoad load = (EntitySectionLoad) transform;
        if (load.refixes$isSectionLoadPending()) {
            ci.cancel();
        } else {
            load.refixes$setSectionLoadPending(true);
        }
    }

    @WrapOperation(
            method =
                    "updateLocation(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/server/core/modules/entity/component/TransformComponent;Lcom/hypixel/hytale/server/core/entity/Dirty;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/component/CommandBuffer;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private static CompletableFuture<?> refixes$applyCurrentSection(
            CompletableFuture<Ref<ChunkStore>> request,
            Consumer<Ref<ChunkStore>> apply,
            Executor executor,
            Operation<CompletableFuture<?>> original,
            @Local(argsOnly = true) Ref<EntityStore> ref,
            @Local(argsOnly = true) TransformComponent transform) {
        if (!((EntitySectionLoad) transform).refixes$isSectionLoadPending()) {
            return original.call(request, apply, executor);
        }
        Vector3d position = transform.getPosition();
        int x = ChunkUtil.chunkCoordinate(position.x());
        int y = ChunkUtil.chunkCoordinate(position.y());
        int z = ChunkUtil.chunkCoordinate(position.z());
        return request.whenCompleteAsync(
                (section, failure) -> refixes$onSectionLoaded(ref, x, y, z, failure == null, section, apply), executor);
    }

    @Unique
    private static void refixes$onSectionLoaded(
            Ref<EntityStore> ref,
            int x,
            int y,
            int z,
            boolean loaded,
            Ref<ChunkStore> section,
            Consumer<Ref<ChunkStore>> apply) {
        if (!ref.isValid()) return;
        TransformComponent transform = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;
        ((EntitySectionLoad) transform).refixes$setSectionLoadPending(false);
        if (!loaded) return;
        Vector3d position = transform.getPosition();
        if (ChunkUtil.chunkCoordinate(position.x()) != x
                || ChunkUtil.chunkCoordinate(position.y()) != y
                || ChunkUtil.chunkCoordinate(position.z()) != z) return;
        apply.accept(section);
    }
}
