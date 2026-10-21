package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.FluidSectionPaletteAccess;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.palette.AbstractSectionPalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(FluidSection.class)
public abstract class MixinFluidSectionPaletteAccess implements FluidSectionPaletteAccess {

    @Shadow
    private AbstractSectionPalette typePalette;

    @Override
    public AbstractSectionPalette refixes$getTypePalette() {
        return this.typePalette;
    }
}
