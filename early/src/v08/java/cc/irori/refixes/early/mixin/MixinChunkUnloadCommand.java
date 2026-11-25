package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.ChunkLoaderColumns;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.commands.world.chunk.ChunkUnloadCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
@Mixin(ChunkUnloadCommand.class)
public class MixinChunkUnloadCommand {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @WrapOperation(
            method = "execute",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/storage/ChunkStore;remove(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/RemoveReason;)V"),
            require = 1,
            allow = 1)
    private void refixes$unloadChunkLoaderSections(
            ChunkStore chunkStore,
            Ref<ChunkStore> chunkRef,
            RemoveReason reason,
            Operation<Void> original,
            @Local(argsOnly = true) World world) {
        WorldChunk column = chunkStore.getStore().getComponent(chunkRef, WorldChunk.getComponentType());
        if (column != null && chunkStore.hasLoadedSections(column.getIndex())) {
            String label = ChunkLoaderColumns.label(world.getName(), column.getIndex());
            if (label != null) {
                List<Ref<ChunkStore>> sections = chunkStore.getLoadedSectionReferences(column.getX(), column.getZ());
                for (Ref<ChunkStore> section : sections) chunkStore.removeSection(section, reason);
                refixes$LOGGER.atInfo().log(
                        "Unloaded %d sections of chunk %d, %d in %s held by chunk loader '%s', which loads them again",
                        sections.size(), column.getX(), column.getZ(), world.getName(), label);
            }
        }
        original.call(chunkStore, chunkRef, reason);
    }
}
