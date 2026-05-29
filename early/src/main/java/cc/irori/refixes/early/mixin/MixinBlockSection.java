package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import com.hypixel.hytale.function.predicate.ObjectPositionBlockFunction;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightDataBuilder;
import java.util.Objects;
import java.util.concurrent.locks.StampedLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Block entity sleep optimization
 * Idle block sections skip forEachTicking when a configurable tick interval hasn't elapsed.
 * Each section maintains its own tick counter, reset when it actually ticks.
 */
@Mixin(BlockSection.class)
public abstract class MixinBlockSection {

    @Shadow
    private int tickingBlocksCountCopy;

    @Shadow
    @Final
    private StampedLock chunkSectionLock;

    @Shadow
    private ChunkLightData localLight;

    @Shadow
    private ChunkLightData globalLight;

    @Unique
    private int refixes$sleepTickCounter;

    @Inject(method = "setLocalLight", at = @At("HEAD"), cancellable = true)
    private void refixes$setLocalLightLocked(ChunkLightDataBuilder localLight, CallbackInfo ci) {
        Objects.requireNonNull(localLight);
        ChunkLightData built = localLight.build();
        long stamp = this.chunkSectionLock.writeLock();
        try {
            this.localLight = built;
        } finally {
            this.chunkSectionLock.unlockWrite(stamp);
        }
        ci.cancel();
    }

    @Inject(method = "setGlobalLight", at = @At("HEAD"), cancellable = true)
    private void refixes$setGlobalLightLocked(ChunkLightDataBuilder globalLight, CallbackInfo ci) {
        Objects.requireNonNull(globalLight);
        ChunkLightData built = globalLight.build();
        long stamp = this.chunkSectionLock.writeLock();
        try {
            this.globalLight = built;
        } finally {
            this.chunkSectionLock.unlockWrite(stamp);
        }
        ci.cancel();
    }

    @Inject(method = "forEachTicking", at = @At("HEAD"), cancellable = true)
    private void refixes$blockEntitySleep(
            Object t,
            Object v,
            int sectionIndex,
            ObjectPositionBlockFunction<?, ?, ?> acceptor,
            CallbackInfoReturnable<Integer> cir) {
        if (tickingBlocksCountCopy == 0) {
            return;
        }
        refixes$sleepTickCounter++;
        if (refixes$sleepTickCounter < EarlyOptions.BLOCK_ENTITY_SLEEP_INTERVAL.get()) {
            cir.setReturnValue(0);
            return;
        }
        refixes$sleepTickCounter = 0;
    }
}
