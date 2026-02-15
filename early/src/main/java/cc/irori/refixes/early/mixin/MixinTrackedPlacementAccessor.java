package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.modules.interaction.blocktrack.TrackedPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TrackedPlacement.class)
public interface MixinTrackedPlacementAccessor {

    @Accessor("blockName")
    String getBlockName();
}
