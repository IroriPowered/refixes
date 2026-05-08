package cc.irori.refixes.system;

import cc.irori.refixes.early.mixin.MixinCraftingManagerAccessor;
import cc.irori.refixes.util.Early;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.builtin.crafting.component.CraftingManager;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class CraftingManagerFixSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = Logs.logger();

    private boolean disabled = false;

    public CraftingManagerFixSystem() {
        Early.requireEnabled();
    }

    @Override
    public void tick(
            float dt,
            int index,
            @NonNull ArchetypeChunk<EntityStore> archetypeChunk,
            @NonNull Store<EntityStore> store,
            @NonNull CommandBuffer<EntityStore> commandBuffer) {
        if (disabled) return;
        try {
            CraftingManager craftingManager = archetypeChunk.getComponent(index, CraftingManager.getComponentType());
            if (craftingManager == null) return;
            if (!(craftingManager instanceof MixinCraftingManagerAccessor accessor)) {
                disabled = true;
                LOGGER.atSevere().log(
                        "CraftingManager is not a MixinCraftingManagerAccessor — Refixes Mixins were not applied (is Hyinit running?). Disabling crafting manager fix.");
                return;
            }
            if (accessor.getBlockType() == null) return;
            Player player = archetypeChunk.getComponent(index, Player.getComponentType());
            if (player == null || !player.getWindowManager().getWindows().isEmpty()) return;
            PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
            if (playerRef == null) return;

            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            craftingManager.clearBench(ref, commandBuffer);
            LOGGER.atWarning().log("Cleared stale crafting bench for player %s", playerRef.getUsername());
        } catch (Exception e) {
            LOGGER.atSevere().withCause(e).log("Failed to apply crafting manager fix");
        }
    }

    @Override
    public @Nullable Query<EntityStore> getQuery() {
        return Player.getComponentType();
    }
}
