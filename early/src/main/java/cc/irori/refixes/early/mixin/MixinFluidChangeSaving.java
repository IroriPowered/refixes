package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.fluid.FluidSystems;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FluidSystems.ReplicateChanges.class)
public abstract class MixinFluidChangeSaving {

    @WrapOperation(
            method =
                    "tick(FILcom/hypixel/hytale/component/ArchetypeChunk;Lcom/hypixel/hytale/component/Store;Lcom/hypixel/hytale/component/CommandBuffer;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/ChunkSection;getChunkColumnReference()Lcom/hypixel/hytale/component/Ref;"),
            require = 1,
            allow = 1)
    private Ref<ChunkStore> refixes$markChangedSection(ChunkSection section, Operation<Ref<ChunkStore>> original) {
        section.markNeedsSaving();
        return original.call(section);
    }
}
