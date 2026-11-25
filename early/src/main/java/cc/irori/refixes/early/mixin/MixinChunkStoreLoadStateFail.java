package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.SectionLockHold;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.StampedLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid$LoadState")
public abstract class MixinChunkStoreLoadStateFail {

    @Shadow
    @Final
    StampedLock lock;

    @Shadow
    int flags;

    @Shadow
    CompletableFuture<Ref<ChunkStore>> future;

    @IfHytale("<0.8.0-pre.1")
    @Shadow
    boolean cleanupAttached;

    @Shadow
    Throwable throwable;

    @Shadow
    long failedWhen;

    @Shadow
    int failedCounter;

    @IfHytale("<0.8.0-pre.1")
    @Inject(method = "fail(Ljava/lang/Throwable;)V", at = @At("HEAD"), cancellable = true)
    private void refixes$failWithoutReentrantWriteLock(Throwable cause, CallbackInfo ci) {
        if (refixes$failHeld(cause, ci)) this.cleanupAttached = false;
    }

    @IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
    @Inject(method = "fail(Ljava/lang/Throwable;)V", at = @At("HEAD"), cancellable = true)
    private void refixes$failWithoutReentrantLock(Throwable cause, CallbackInfo ci) {
        refixes$failHeld(cause, ci);
    }

    @IfHytale("<0.8.0-pre.1")
    @Inject(method = "checkAndResetBackoff()Z", at = @At("HEAD"))
    private void refixes$clearFailedChainBeforeRetry(CallbackInfoReturnable<Boolean> cir) {
        if (refixes$clearFailedChain()) this.cleanupAttached = false;
    }

    @IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
    @Inject(method = "checkAndResetBackoff()Z", at = @At("HEAD"))
    private void refixes$clearFailedBeforeRetry(CallbackInfoReturnable<Boolean> cir) {
        refixes$clearFailedChain();
    }

    @Unique
    private boolean refixes$failHeld(Throwable cause, CallbackInfo ci) {
        if (!SectionLockHold.heldByCurrentThread(this.lock)) {
            return false;
        }
        this.flags = GetChunkFlags.NONE;
        this.future = null;
        this.throwable = cause;
        this.failedWhen = System.nanoTime();
        this.failedCounter++;
        ci.cancel();
        return true;
    }

    @Unique
    private boolean refixes$clearFailedChain() {
        if (this.throwable == null) return false;
        this.flags = GetChunkFlags.NONE;
        this.future = null;
        return true;
    }
}
