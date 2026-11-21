package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.BlockSectionSaveOwner;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkGrid;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkGrid.class)
public class MixinChunkGridSectionSaveOwner {

    @Inject(
            method = "postLoadChunkSection",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/Store;addEntity(Lcom/hypixel/hytale/component/Holder;Lcom/hypixel/hytale/component/AddReason;)Lcom/hypixel/hytale/component/Ref;"),
            require = 1,
            allow = 1)
    private void refixes$linkSaveOwner(
            Ref<ChunkStore> chunkRef,
            int x,
            int y,
            int z,
            Holder<ChunkStore> section,
            boolean newlyGenerated,
            boolean isTicking,
            CallbackInfoReturnable<Ref<ChunkStore>> cir) {
        BlockSection blocks = section.getComponent(BlockSection.getComponentType());
        ChunkSection owner = section.getComponent(ChunkSection.getComponentType());
        if (blocks != null && owner != null) ((BlockSectionSaveOwner) blocks).refixes$linkSaveOwner(owner);
    }
}
