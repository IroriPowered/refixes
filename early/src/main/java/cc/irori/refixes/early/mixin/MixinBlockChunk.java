package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// BlockChunk section cache which caches the last accessed BlockSection from getSectionAtBlockY().

@Mixin(BlockChunk.class)
public class MixinBlockChunk {

    @Unique
    private int refixes$cachedSectionIndex = -1;

    @Unique
    private BlockSection refixes$cachedSectionRef;

    @Inject(method = "getSectionAtBlockY", at = @At("HEAD"), cancellable = true)
    private void refixes$sectionCacheCheck(int y, CallbackInfoReturnable<BlockSection> cir) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.SECTION_CACHE_ENABLED.get()) {
            return;
        }
        int index = ChunkUtil.indexSection(y);
        if (index == refixes$cachedSectionIndex && refixes$cachedSectionRef != null) {
            cir.setReturnValue(refixes$cachedSectionRef);
        }
    }

    @Inject(method = "getSectionAtBlockY", at = @At("RETURN"))
    private void refixes$sectionCacheUpdate(int y, CallbackInfoReturnable<BlockSection> cir) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.SECTION_CACHE_ENABLED.get()) {
            return;
        }
        refixes$cachedSectionIndex = ChunkUtil.indexSection(y);
        refixes$cachedSectionRef = cir.getReturnValue();
    }
}
