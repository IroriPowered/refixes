package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.ChunkSaveQueue;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.component.ChunkSavingSystems;
import java.util.Deque;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkSavingSystems.Data.class)
public abstract class MixinChunkSaveQueueData implements ChunkSaveQueue {

    @Shadow
    @Final
    private Set<Ref<ChunkStore>> set;

    @Shadow
    @Final
    private Deque<Ref<ChunkStore>> queue;

    @Override
    public boolean refixes$enqueueLast(Ref<ChunkStore> reference) {
        if (!set.add(reference)) return false;
        boolean behind = !queue.isEmpty();
        queue.addLast(reference);
        return behind;
    }
}
