package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.locks.StampedLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkTracker.class)
public abstract class MixinChunkTrackerQueryReentry {

    @Shadow
    @Final
    private StampedLock lock;

    @Unique
    private Thread refixes$writeOwner;

    @WrapOperation(
            method = {"tick", "lambda$_loadSectionAsync$1", "lambda$_loadChunkAsync$1"},
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/StampedLock;writeLock()J"),
            require = 3,
            allow = 3)
    private long refixes$ownWrite(StampedLock target, Operation<Long> original) {
        long stamp = original.call(target);
        if (target == lock) refixes$writeOwner = Thread.currentThread();
        return stamp;
    }

    @WrapOperation(
            method = {"tick", "lambda$_loadSectionAsync$1", "lambda$_loadChunkAsync$1"},
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/StampedLock;unlockWrite(J)V"),
            require = 6,
            allow = 6)
    private void refixes$releaseWrite(StampedLock target, long stamp, Operation<Void> original) {
        if (target == lock) refixes$writeOwner = null;
        original.call(target, stamp);
    }

    @WrapOperation(
            method = {
                "isLoaded(J)Z",
                "isLoaded(III)Z",
                "getLoadedSectionsCount",
                "getLoadingSectionsCount",
                "getLoadedChunksDebug",
                "forEachLoadedSection"
            },
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/StampedLock;readLock()J"),
            require = 6,
            allow = 6)
    private long refixes$ownedRead(StampedLock target, Operation<Long> original) {
        if (target == lock && refixes$writeOwner == Thread.currentThread()) return 0L;
        return original.call(target);
    }

    @WrapOperation(
            method = {
                "isLoaded(J)Z",
                "isLoaded(III)Z",
                "getLoadedSectionsCount",
                "getLoadingSectionsCount",
                "getLoadedChunksDebug",
                "forEachLoadedSection"
            },
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/locks/StampedLock;unlockRead(J)V"),
            require = 13,
            allow = 13)
    private void refixes$ownedReadRelease(StampedLock target, long stamp, Operation<Void> original) {
        if (stamp == 0L) return;
        original.call(target, stamp);
    }
}
