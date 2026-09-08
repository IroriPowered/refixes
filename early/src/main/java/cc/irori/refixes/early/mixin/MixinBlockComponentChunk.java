package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockComponentSection.class)
public class MixinBlockComponentChunk {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Inject(
            method = {
                "addBlockReference(SLcom/hypixel/hytale/component/Ref;)V",
                "loadBlockReference(SLcom/hypixel/hytale/component/Ref;)V"
            },
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/lang/IllegalArgumentException;"),
            cancellable = true)
    private void refixes$ignoreDuplicateBlockComponentException(
            short index, Ref<ChunkStore> reference, CallbackInfo ci) {
        refixes$LOGGER.atWarning().log("BlockComponentSection: Duplicate block component ignored");
        ci.cancel();
    }

    @Inject(
            method = {
                "addBlockHolder(SLcom/hypixel/hytale/component/Holder;)V",
                "storeBlockHolder(SLcom/hypixel/hytale/component/Holder;)V"
            },
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Ljava/lang/IllegalArgumentException;"),
            cancellable = true)
    private void refixes$ignoreDuplicateBlockComponentHolder(short index, Holder<ChunkStore> holder, CallbackInfo ci) {
        refixes$LOGGER.atWarning().log("BlockComponentSection: Duplicate block component holder ignored");
        ci.cancel();
    }
}
