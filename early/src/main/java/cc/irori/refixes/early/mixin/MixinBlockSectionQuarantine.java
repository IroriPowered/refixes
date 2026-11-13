package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.QuarantinedSection;
import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.RecoveryPolicy;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.io.IOException;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGrid.class)
public abstract class MixinBlockSectionQuarantine {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Shadow
    @Final
    protected World world;

    @Inject(
            method =
                    "preLoadChunkSectionAsync(Lcom/hypixel/hytale/server/core/universe/world/chunk/heightmap/HeightmapColumn;IIILcom/hypixel/hytale/component/Holder;Z)Lcom/hypixel/hytale/component/Holder;",
            at = @At("HEAD"))
    private void refixes$labelQuarantine(
            HeightmapColumn heightmap,
            int x,
            int y,
            int z,
            Holder<ChunkStore> holder,
            boolean newlyGenerated,
            CallbackInfoReturnable<Holder<ChunkStore>> cir) {
        if (!(holder.getComponent(BlockSection.getComponentType()) instanceof QuarantinedSection section)) return;
        Path quarantined = section.refixes$takeQuarantine();
        if (quarantined == null) return;
        try {
            quarantined = RecoveryPolicy.label(quarantined, world.getName(), x, y, z);
        } catch (IOException | RuntimeException e) {
            refixes$LOGGER.atWarning().withCause(e).log("Could not label quarantined block section %s", quarantined);
        }
        refixes$LOGGER.atWarning().log(
                "Corrupt block section of world '%s' at chunk %d,%d section %d was reset to empty; original data quarantined at %s",
                world.getName(), x, z, y, quarantined);
    }
}
