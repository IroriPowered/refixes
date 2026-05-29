package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.builtin.adventure.teleporter.system.TurnOffTeleportersSystem;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * Defers TurnOffTeleportersSystem.updatePortalBlocksInWorld() calls to world.execute()
 * instead of running inline during onEntityAdded/onEntityRemove callbacks.
 */
@Mixin(TurnOffTeleportersSystem.class)
public class MixinTurnOffTeleportersSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Overwrite
    public void onEntityAdded(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull AddReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (reason == AddReason.LOAD) {
            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                try {
                    TurnOffTeleportersSystem.updatePortalBlocksInWorld(world);
                } catch (Exception e) {
                    refixes$LOGGER.atWarning().withCause(e).log(
                            "TurnOffTeleportersSystem#onEntityAdded(): Failed to update portal blocks in %s",
                            world.getName());
                }
            });
        }
    }

    @Overwrite
    public void onEntityRemove(
            @Nonnull Ref<ChunkStore> ref,
            @Nonnull RemoveReason reason,
            @Nonnull Store<ChunkStore> store,
            @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        if (reason == RemoveReason.REMOVE) {
            World world = store.getExternalData().getWorld();
            world.execute(() -> {
                try {
                    TurnOffTeleportersSystem.updatePortalBlocksInWorld(world);
                } catch (Exception e) {
                    refixes$LOGGER.atWarning().withCause(e).log(
                            "TurnOffTeleportersSystem#onEntityRemove(): Failed to update portal blocks in %s",
                            world.getName());
                }
            });
        }
    }
}
