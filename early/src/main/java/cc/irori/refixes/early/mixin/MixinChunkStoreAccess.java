package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.ChunkStoreAccess;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkSaver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkStore.class)
public interface MixinChunkStoreAccess extends ChunkStoreAccess {

    @Override
    @Accessor("saver")
    void refixes$setSaver(IChunkSaver saver);
}
