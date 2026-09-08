package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.chunk.section.palette.EmptySectionPalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guards BlockSection.deserialize() against corrupt section data (#14).
 * If deserialization fails, the section is left empty rather than crashing the server.
 */
@Mixin(BlockSection.class)
public abstract class MixinBlockSectionSafety {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @Shadow
    private com.hypixel.hytale.server.core.universe.world.chunk.section.palette.AbstractSectionPalette chunkSection;

    @Shadow
    private com.hypixel.hytale.server.core.universe.world.chunk.section.palette.AbstractSectionPalette fillerSection;

    @Shadow
    private com.hypixel.hytale.server.core.universe.world.chunk.section.palette.AbstractSectionPalette rotationSection;

    @Shadow
    private java.util.BitSet tickingBlocks;

    @Shadow
    private int tickingBlocksCount;

    @Shadow
    private ChunkLightData localLight;

    @Shadow
    private ChunkLightData globalLight;

    @Shadow
    private short localChangeCounter;

    @Shadow
    private short globalChangeCounter;

    @Shadow
    public abstract void deserialize(byte[] bytes, ExtraInfo extraInfo);

    @Shadow
    public abstract void invalidate();

    @Inject(method = "deserialize([BLcom/hypixel/hytale/codec/ExtraInfo;)V", at = @At("HEAD"), cancellable = true)
    private void refixes$safeDeserialize(byte[] bytes, ExtraInfo extraInfo, CallbackInfo ci) {
        if (refixes$WRAPPING.get()) {
            return;
        }
        ci.cancel();
        refixes$WRAPPING.set(true);
        try {
            deserialize(bytes, extraInfo);
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "BlockSection#deserialize(): Corrupt block section data, resetting to empty");
            chunkSection = EmptySectionPalette.INSTANCE;
            fillerSection = EmptySectionPalette.INSTANCE;
            rotationSection = EmptySectionPalette.INSTANCE;
            tickingBlocks = new java.util.BitSet();
            tickingBlocksCount = 0;
            localLight = ChunkLightData.EMPTY;
            globalLight = ChunkLightData.EMPTY;
            localChangeCounter = 0;
            globalChangeCounter = 0;
            invalidate();
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
