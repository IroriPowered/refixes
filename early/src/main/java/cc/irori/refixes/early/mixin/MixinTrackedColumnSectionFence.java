package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.TrackedColumnSectionFence;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker$TrackedColumn")
public abstract class MixinTrackedColumnSectionFence implements TrackedColumnSectionFence {

    @Shadow
    @Final
    private IntSet loadingSections;

    @Shadow
    @Final
    private IntSet loadedSections;

    @Unique
    private Int2LongOpenHashMap refixes$fullFloor;

    @Unique
    private Int2LongOpenHashMap refixes$deltaWritten;

    @Unique
    private Int2LongOpenHashMap refixes$fluidFloor;

    @Unique
    private Int2LongOpenHashMap refixes$fluidDeltaWritten;

    @Unique
    private IntOpenHashSet refixes$dirtyWhileLoading;

    @Override
    public void refixes$markLoadStart(int y, long sequence) {
        if (refixes$fullFloor == null) refixes$fullFloor = new Int2LongOpenHashMap(4);
        refixes$fullFloor.put(y, sequence);
        if (refixes$fluidFloor == null) refixes$fluidFloor = new Int2LongOpenHashMap(4);
        refixes$fluidFloor.put(y, sequence);
        if (refixes$dirtyWhileLoading != null) {
            refixes$dirtyWhileLoading.remove(y);
            refixes$dirtyWhileLoading.removeIf(k -> !loadingSections.contains(k));
        }
        int live = loadingSections.size() + loadedSections.size() + 1;
        if (refixes$fullFloor.size() > 2 * live) refixes$prune(refixes$fullFloor, y);
        if (refixes$deltaWritten != null && refixes$deltaWritten.size() > 2 * live)
            refixes$prune(refixes$deltaWritten, y);
        if (refixes$fluidFloor.size() > 2 * live) refixes$prune(refixes$fluidFloor, y);
        if (refixes$fluidDeltaWritten != null && refixes$fluidDeltaWritten.size() > 2 * live)
            refixes$prune(refixes$fluidDeltaWritten, y);
    }

    @Unique
    private void refixes$prune(Int2LongOpenHashMap map, int keep) {
        map.keySet().removeIf(k -> k != keep && !loadingSections.contains(k) && !loadedSections.contains(k));
    }

    @Override
    public void refixes$forget(int y) {
        if (refixes$fullFloor != null) refixes$fullFloor.remove(y);
        if (refixes$deltaWritten != null) refixes$deltaWritten.remove(y);
        if (refixes$fluidFloor != null) refixes$fluidFloor.remove(y);
        if (refixes$fluidDeltaWritten != null) refixes$fluidDeltaWritten.remove(y);
        if (refixes$dirtyWhileLoading != null) refixes$dirtyWhileLoading.remove(y);
    }

    @Override
    public boolean refixes$noteDelta(int y, long sequence, boolean fluid) {
        if (!loadedSections.contains(y)) return false;
        if (fluid) {
            if (refixes$fluidDeltaWritten == null) refixes$fluidDeltaWritten = new Int2LongOpenHashMap(4);
            refixes$fluidDeltaWritten.put(y, sequence);
            return true;
        }
        if (refixes$deltaWritten == null) refixes$deltaWritten = new Int2LongOpenHashMap(4);
        refixes$deltaWritten.put(y, sequence);
        return true;
    }

    @Override
    public int refixes$decideSnapshot(int y, long ticket, boolean fluid) {
        Int2LongOpenHashMap floor = fluid ? refixes$fluidFloor : refixes$fullFloor;
        Int2LongOpenHashMap written = fluid ? refixes$fluidDeltaWritten : refixes$deltaWritten;
        if (!loadedSections.contains(y)) {
            if (loadingSections.contains(y) && (floor == null || floor.get(y) < ticket)) return DIRTY;
            return SKIP;
        }
        if (floor != null && floor.get(y) > ticket) return SKIP;
        if (written != null && written.get(y) > ticket) return RELOAD;
        return WRITE;
    }

    @Override
    public void refixes$commitSnapshot(int y, long ticket, boolean fluid) {
        if (fluid) {
            if (refixes$fluidFloor == null) refixes$fluidFloor = new Int2LongOpenHashMap(4);
            if (refixes$fluidFloor.get(y) < ticket) refixes$fluidFloor.put(y, ticket);
            return;
        }
        if (refixes$fullFloor == null) refixes$fullFloor = new Int2LongOpenHashMap(4);
        if (refixes$fullFloor.get(y) < ticket) refixes$fullFloor.put(y, ticket);
    }

    @Override
    public boolean refixes$isLoading(int y) {
        return loadingSections.contains(y);
    }

    @Override
    public boolean refixes$markDirtyIfLoading(int y) {
        if (!loadingSections.contains(y)) return false;
        if (refixes$dirtyWhileLoading == null) refixes$dirtyWhileLoading = new IntOpenHashSet(4);
        refixes$dirtyWhileLoading.add(y);
        return true;
    }

    @Override
    public boolean refixes$takeDirty(int y) {
        return refixes$dirtyWhileLoading != null && refixes$dirtyWhileLoading.remove(y);
    }
}
