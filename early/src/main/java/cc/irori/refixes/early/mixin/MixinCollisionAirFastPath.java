package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.modules.collision.CollisionConfig;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CollisionConfig.class)
public class MixinCollisionAirFastPath {

    @Unique
    private boolean refixes$airSection;

    @Redirect(
            method = "canCollide(III)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;get(III)I"))
    private int refixes$getBlock(BlockSection section, int x, int y, int z) {
        this.refixes$airSection = section.isSolidAir();
        return this.refixes$airSection ? 0 : section.get(x, y, z);
    }

    @Redirect(
            method = "canCollide(III)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;getFiller(III)I"))
    private int refixes$getFiller(BlockSection section, int x, int y, int z) {
        return this.refixes$airSection ? 0 : section.getFiller(x, y, z);
    }

    @Redirect(
            method = "canCollide(III)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;getRotationIndex(III)I"))
    private int refixes$getRotation(BlockSection section, int x, int y, int z) {
        return this.refixes$airSection ? 0 : section.getRotationIndex(x, y, z);
    }
}
