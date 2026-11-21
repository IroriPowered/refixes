package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.BlockSectionSaveOwner;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockSection.class)
public abstract class MixinBlockSectionSaveMarks implements BlockSectionSaveOwner {

    @Unique
    private volatile ChunkSection refixes$saveOwner;

    @Unique
    private volatile boolean refixes$unmarkedChange;

    @Inject(
            method = "set(IIII)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/chunk/section/BlockSection;invalidateLocalLight()V"),
            require = 1,
            allow = 1)
    private void refixes$markBlockChange(
            int blockIdx, int blockId, int rotation, int filler, CallbackInfoReturnable<Boolean> cir) {
        ChunkSection owner = refixes$saveOwner;
        if (owner != null) {
            owner.markNeedsSaving();
        } else {
            refixes$unmarkedChange = true;
        }
    }

    @Override
    public void refixes$linkSaveOwner(ChunkSection owner) {
        ChunkSection previous = refixes$saveOwner;
        refixes$saveOwner = owner;
        if (refixes$unmarkedChange || (previous != null && previous != owner)) {
            refixes$unmarkedChange = false;
            owner.markNeedsSaving();
        }
    }
}
