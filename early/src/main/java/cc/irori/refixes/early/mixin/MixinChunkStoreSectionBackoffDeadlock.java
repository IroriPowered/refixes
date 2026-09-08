package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SectionLockHold;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.locks.StampedLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkStore.class)
public abstract class MixinChunkStoreSectionBackoffDeadlock {

    @WrapOperation(
            method = "getChunkSectionReferenceAsync(IIII)Ljava/util/concurrent/CompletableFuture;",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/StampedLock;writeLock()J"))
    private long refixes$trackSectionWriteLock(StampedLock lock, Operation<Long> original) {
        long stamp = original.call(lock);
        SectionLockHold.acquire(lock);
        return stamp;
    }

    @WrapOperation(
            method = "getChunkSectionReferenceAsync(IIII)Ljava/util/concurrent/CompletableFuture;",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/StampedLock;unlockWrite(J)V"))
    private void refixes$trackSectionUnlockWrite(StampedLock lock, long stamp, Operation<Void> original) {
        try {
            original.call(lock, stamp);
        } finally {
            SectionLockHold.release(lock);
        }
    }
}
