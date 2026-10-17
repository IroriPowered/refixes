package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.BlockComponentSectionAccess;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.shorts.Short2ReferenceMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockComponentSection.class)
public interface MixinBlockComponentSectionAccess extends BlockComponentSectionAccess {

    @Override
    @Accessor("blockReferences")
    Short2ReferenceMap<Ref<ChunkStore>> refixes$getBlockReferences();
}
