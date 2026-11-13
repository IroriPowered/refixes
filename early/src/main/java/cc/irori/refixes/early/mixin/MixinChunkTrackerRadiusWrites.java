package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.PlayerRadiusAccess;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.atomic.AtomicLong;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkTracker.class)
public abstract class MixinChunkTrackerRadiusWrites implements PlayerRadiusAccess {

    @Unique
    private static final AtomicLong refixes$radiusWriteSequence = new AtomicLong();

    @Unique
    private long refixes$hotRadiusRevision;

    @Unique
    private long refixes$minRadiusRevision;

    @Unique
    private long refixes$viewRadiusRevision;

    @WrapMethod(method = "setMaxHotLoadedRadius", require = 1)
    private void refixes$trackHotRadius(int radius, Operation<Void> original) {
        synchronized (this) {
            original.call(radius);
            refixes$hotRadiusRevision = refixes$radiusWriteSequence.incrementAndGet();
        }
    }

    @WrapMethod(method = "setMinLoadedRadius", require = 1)
    private void refixes$trackMinRadius(int radius, Operation<Void> original) {
        synchronized (this) {
            original.call(radius);
            refixes$minRadiusRevision = refixes$radiusWriteSequence.incrementAndGet();
        }
    }

    @Override
    public synchronized long refixes$getHotRadiusRevision() {
        return refixes$hotRadiusRevision;
    }

    @Override
    public synchronized void refixes$restoreHotRadiusRevision(long revision) {
        refixes$hotRadiusRevision = revision;
    }

    @Override
    public synchronized long refixes$getMinRadiusRevision() {
        return refixes$minRadiusRevision;
    }

    @Override
    public synchronized long refixes$getViewRadiusRevision() {
        return refixes$viewRadiusRevision;
    }

    @Override
    public synchronized void refixes$recordViewRadiusWrite() {
        refixes$viewRadiusRevision = refixes$radiusWriteSequence.incrementAndGet();
    }
}
