package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.QuarantinedSection;
import cc.irori.refixes.early.util.IncidentCounters;
import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.RecoveryPolicy;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Options;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.chunk.section.palette.EmptySectionPalette;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.nio.file.Path;
import joptsimple.OptionSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Guards BlockSection.deserialize() against corrupt section data (#14).
 * The section is reset only after the corrupt data is quarantined.
 */
@Mixin(BlockSection.class)
public abstract class MixinBlockSectionSafety implements QuarantinedSection {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private Path refixes$quarantine;

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

    @Override
    public Path refixes$takeQuarantine() {
        Path quarantine = refixes$quarantine;
        refixes$quarantine = null;
        return quarantine;
    }

    @WrapMethod(method = "deserialize([BLcom/hypixel/hytale/codec/ExtraInfo;)V")
    private void refixes$safeDeserialize(byte[] bytes, ExtraInfo extraInfo, Operation<Void> original) {
        try {
            original.call(bytes, extraInfo);
        } catch (Exception failure) {
            if (refixes$isUnsupportedVersion(failure) || refixes$verifyingWorlds()) {
                throw failure;
            }
            try {
                Path quarantine =
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
                refixes$quarantine = quarantine;
                IncidentCounters.SECTIONS_QUARANTINED.increment();
                refixes$LOGGER.atWarning().withCause(failure).log(
                        "BlockSection#deserialize(): Corrupt data reset to empty after quarantine at %s", quarantine);
            } catch (Exception quarantineFailure) {
                IncidentCounters.QUARANTINE_FAILURES.increment();
                failure.addSuppressed(quarantineFailure);
                refixes$LOGGER.atSevere().withCause(failure).log(
                        "BlockSection#deserialize(): Quarantine failed. Corrupt section was not reset");
                throw failure;
            }
        }
    }

    @Unique
    private static boolean refixes$isUnsupportedVersion(Exception failure) {
        return failure instanceof IllegalArgumentException
                && failure.getMessage() != null
                && failure.getMessage().startsWith("Version not supported");
    }

    @Unique
    private static boolean refixes$verifyingWorlds() {
        OptionSet options = Options.getOptionSet();
        return options != null && options.has(Options.VERIFY_WORLDS);
    }
}
