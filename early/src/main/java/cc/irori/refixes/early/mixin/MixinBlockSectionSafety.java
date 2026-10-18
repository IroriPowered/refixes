package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.RecoveryPolicy;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.chunk.section.palette.EmptySectionPalette;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Guards BlockSection.deserialize() against corrupt section data (#14).
 * The section is reset only after the corrupt data is quarantined.
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
    public abstract void invalidate();

    @WrapMethod(method = "deserialize([BLcom/hypixel/hytale/codec/ExtraInfo;)V")
    private void refixes$safeDeserialize(byte[] bytes, ExtraInfo extraInfo, Operation<Void> original) {
        if (refixes$WRAPPING.get()) {
            original.call(bytes, extraInfo);
            return;
        }
        refixes$WRAPPING.set(true);
        try {
            original.call(bytes, extraInfo);
        } catch (Exception failure) {
            try {
                java.nio.file.Path quarantine =
                        RecoveryPolicy.quarantineThen(bytes, RecoveryPolicy.blockSectionQuarantineDirectory(), () -> {
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
                        });
                refixes$LOGGER.atWarning().withCause(failure).log(
                        "BlockSection#deserialize(): Corrupt data reset to empty after quarantine at %s", quarantine);
            } catch (Exception quarantineFailure) {
                failure.addSuppressed(quarantineFailure);
                refixes$LOGGER.atSevere().withCause(failure).log(
                        "BlockSection#deserialize(): Quarantine failed. Corrupt section was not reset");
                throw failure;
            }
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
