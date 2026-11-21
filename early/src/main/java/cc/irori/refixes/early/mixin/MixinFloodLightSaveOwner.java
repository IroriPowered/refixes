package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.BlockSectionSaveOwner;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.lighting.FloodLightCalculation;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FloodLightCalculation.class)
public class MixinFloodLightSaveOwner {

    @Inject(
            method = "updateLocalLight",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;setLocalLight(Lcom/hypixel/hytale/server/core/universe/world/chunk/section/ChunkLightDataBuilder;)V"),
            require = 1,
            allow = 1)
    private void refixes$linkSaveOwner(
            CallbackInfoReturnable<?> cir,
            @Local(argsOnly = true) ChunkSection section,
            @Local(argsOnly = true) BlockSection blocks) {
        ((BlockSectionSaveOwner) blocks).refixes$linkSaveOwner(section);
    }
}
