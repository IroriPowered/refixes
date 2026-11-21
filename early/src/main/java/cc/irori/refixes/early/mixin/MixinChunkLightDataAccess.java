package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.ChunkLightDataAccess;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkLightData;
import java.lang.foreign.MemorySegment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkLightData.class)
public interface MixinChunkLightDataAccess extends ChunkLightDataAccess {

    @Override
    @Accessor("lightData")
    MemorySegment refixes$getLightData();
}
