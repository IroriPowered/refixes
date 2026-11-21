package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import cc.irori.refixes.early.util.PatchCounters;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TickingThread.class)
public class MixinTickSleep {

    @Unique
    private static final long refixes$PROBE_NANOS = 100_000L;

    @Unique
    private static final long refixes$MAX_GUARD_NANOS = 1_000_000L;

    @Unique
    private static volatile long refixes$baseGuard;

    @Shadow
    private int tickStepNanos;

    @Unique
    private long refixes$tickStart;

    @Unique
    private long refixes$tickEnd;

    @Unique
    private long refixes$guard;

    @Inject(
            method = "run",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/server/core/util/thread/TickingThread;tick(F)V"),
            require = 1,
            allow = 1)
    private void refixes$markTickStart(CallbackInfo ci) {
        refixes$tickStart = System.nanoTime();
    }

    @Inject(
            method = "run",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/hytale/server/core/util/thread/TickingThread;tick(F)V",
                            shift = At.Shift.AFTER),
            require = 1,
            allow = 1)
    private void refixes$markTickEnd(CallbackInfo ci) {
        refixes$tickEnd = System.nanoTime();
    }

    @Redirect(
            method = "run",
            at = @At(value = "INVOKE", target = "Ljava/lang/Thread;onSpinWait()V"),
            require = 1,
            allow = 1)
    private void refixes$hybridWait() {
        if (PatchCounters.ENABLED) PatchCounters.TICK_WAIT.countReached();
        if (refixes$tickEnd - refixes$tickStart >= tickStepNanos >> 1) {
            Thread.onSpinWait();
            return;
        }
        long guard = refixes$guard;
        long now = System.nanoTime();
        long park = refixes$tickStart + tickStepNanos - now - guard;
        if (guard < 0L || park <= 0L) {
            Thread.onSpinWait();
            return;
        }
        if (PatchCounters.ENABLED) PatchCounters.TICK_WAIT.countEffect();
        LockSupport.parkNanos(park);
        long target = Math.min(2L * (System.nanoTime() - now - park), refixes$MAX_GUARD_NANOS);
        long base = refixes$baseGuard;
        refixes$guard = Math.max(Math.max(target, base), guard - ((guard - base) >> 4));
    }

    @Inject(method = "run", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$calibrateBeforeLoop(CallbackInfo ci) {
        long base = refixes$baseGuard;
        refixes$guard = base != 0L ? base : refixes$calibrate();
    }

    @Unique
    private static synchronized long refixes$calibrate() {
        long base = refixes$baseGuard;
        if (base != 0L) {
            return base;
        }
        long[] overshoots = new long[9];
        for (int i = 0; i < overshoots.length; i++) {
            long start = System.nanoTime();
            LockSupport.parkNanos(refixes$PROBE_NANOS);
            overshoots[i] = System.nanoTime() - start - refixes$PROBE_NANOS;
        }
        Arrays.sort(overshoots);
        long typical = overshoots[overshoots.length / 2];
        if (2L * typical > refixes$MAX_GUARD_NANOS) {
            base = -1L;
            Logs.logger()
                    .atInfo()
                    .log(
                            "TickWait: a %d us park typically overshot by %d us here, keeping vanilla's tick spin",
                            refixes$PROBE_NANOS / 1_000L, typical / 1_000L);
        } else {
            base = Math.max(2L * typical, 1L);
            Logs.logger()
                    .atInfo()
                    .log(
                            "TickWait: a %d us park typically overshot by %d us here, waking %d us before each tick",
                            refixes$PROBE_NANOS / 1_000L, typical / 1_000L, base / 1_000L);
        }
        refixes$baseGuard = base;
        return base;
    }
}
