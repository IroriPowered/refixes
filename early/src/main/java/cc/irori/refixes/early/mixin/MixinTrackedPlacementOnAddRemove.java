package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.modules.interaction.blocktrack.BlockCounter;
import com.hypixel.hytale.server.core.modules.interaction.blocktrack.TrackedPlacement;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(TrackedPlacement.OnAddRemove.class)
public class MixinTrackedPlacementOnAddRemove {

    @WrapWithCondition(
            method = "onEntityRemove",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/modules/interaction/blocktrack/BlockCounter;untrackBlock(Ljava/lang/String;)V"))
    private boolean refixes$skipUnnamedPlacement(BlockCounter counter, String blockName) {
        return blockName != null;
    }
}
