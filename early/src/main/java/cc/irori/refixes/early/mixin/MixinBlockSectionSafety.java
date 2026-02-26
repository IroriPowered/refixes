package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
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
    public abstract void deserialize(byte[] bytes, ExtraInfo extraInfo);

    @Inject(method = "deserialize([BLcom/hypixel/hytale/codec/ExtraInfo;)V", at = @At("HEAD"), cancellable = true)
    private void refixes$safeDeserialize(byte[] bytes, ExtraInfo extraInfo, CallbackInfo ci) {
        if (!EarlyOptions.isAvailable() || !EarlyOptions.CORRUPT_SECTION_PROTECTION.get()) {
            return;
        }
        if (refixes$WRAPPING.get()) {
            return;
        }
        ci.cancel();
        refixes$WRAPPING.set(true);
        try {
            deserialize(bytes, extraInfo);
        } catch (Exception e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "BlockSection#deserialize(): Corrupt block section data, leaving section empty");
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
