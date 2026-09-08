package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SectionLockHold;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.StampedLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.hypixel.hytale.server.core.universe.world.storage.ChunkStore$LoadState")
public abstract class MixinChunkStoreLoadStateFail {

    @Shadow
    @Final
    StampedLock lock;

    @Shadow
    int flags;

    @Shadow
    CompletableFuture<Ref<ChunkStore>> future;

    @Shadow
    boolean cleanupAttached;

    @Shadow
    Throwable throwable;

    @Shadow
    long failedWhen;

    @Shadow
    int failedCounter;

    @Inject(method = "fail", at = @At("HEAD"), cancellable = true)
    private void refixes$failWithoutReentrantWriteLock(Throwable cause, CallbackInfo ci) {
        if (!SectionLockHold.heldByCurrentThread(this.lock)) {
            return;
        }
        this.flags = GetChunkFlags.NONE;
        this.future = null;
        this.cleanupAttached = false;
        this.throwable = cause;
        this.failedWhen = System.nanoTime();
        this.failedCounter++;
        ci.cancel();
    }

    @Inject(method = "checkAndResetBackoff", at = @At("HEAD"))
    private void refixes$clearFailedChainBeforeRetry(CallbackInfoReturnable<Boolean> cir) {
        if (this.throwable != null) {
            this.flags = GetChunkFlags.NONE;
            this.future = null;
            this.cleanupAttached = false;
        }
    }
}
