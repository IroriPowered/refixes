package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.ChunkGridAccess;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid;
import com.hypixel.hytale.server.core.universe.world.storage.IChunkLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkGrid.class)
public interface MixinChunkGridAccess extends ChunkGridAccess {

    @Override
    @Accessor("loader")
    void refixes$setLoader(IChunkLoader loader);
}
