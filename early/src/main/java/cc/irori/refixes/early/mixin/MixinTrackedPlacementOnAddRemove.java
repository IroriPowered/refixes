package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.interaction.blocktrack.BlockCounter;
import com.hypixel.hytale.server.core.modules.interaction.blocktrack.TrackedPlacement;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TrackedPlacement.OnAddRemove.class)
public class MixinTrackedPlacementOnAddRemove {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Inject(
            method = "onEntityRemove",
            at = @At("HEAD"),
            cancellable = true
    )
    private void refixes$fixOnEntityRemove(Ref<ChunkStore> ref, RemoveReason reason, Store<ChunkStore> store, CommandBuffer<ChunkStore> commandBuffer, CallbackInfo ci) {
        ci.cancel();

        if (reason != RemoveReason.REMOVE) {
            return;
        }
        TrackedPlacement tracked = commandBuffer.getComponent(ref, TrackedPlacement.getComponentType());
        if (tracked == null) {
            refixes$LOGGER.atWarning().log("TrackedPlacement.OnAddRemove#onEntityRemove(): TrackedPlacement is null");
            return;
        }
        String blockName = ((MixinTrackedPlacementAccessor) tracked).getBlockName();
        if (blockName == null || blockName.isEmpty()) {
            refixes$LOGGER.atWarning().log("TrackedPlacement.OnAddRemove#onEntityRemove(): Block name is null or empty");
            return;
        }

        BlockCounter counter = commandBuffer.getResource(BlockCounter.getResourceType());
        counter.untrackBlock(blockName);
    }
}
