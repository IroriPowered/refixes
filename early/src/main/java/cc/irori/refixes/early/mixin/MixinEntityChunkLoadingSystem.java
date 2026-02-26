package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonTicking;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.EntityChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * Fixes NPE in EntityChunkLoadingSystem#onComponentRemoved by adding null checks
 * for WorldChunk, EntityChunk, entity holders, and TransformComponent.
 * Entities with null TransformComponent are skipped and their chunk is marked dirty.
 */
@Mixin(targets = "com.hypixel.hytale.server.core.universe.world.chunk.EntityChunk$EntityChunkLoadingSystem")
public class MixinEntityChunkLoadingSystem {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Overwrite
    public void onComponentRemoved(
            @Nonnull Ref ref,
            @Nonnull NonTicking component,
            @Nonnull Store store,
            @Nonnull CommandBuffer commandBuffer) {
        World world = ((ChunkStore) store.getExternalData()).getWorld();

        WorldChunk worldChunkComponent = (WorldChunk) store.getComponent(ref, WorldChunk.getComponentType());
        if (worldChunkComponent == null) {
            return;
        }

        EntityChunk entityChunkComponent = (EntityChunk) store.getComponent(ref, EntityChunk.getComponentType());
        if (entityChunkComponent == null) {
            return;
        }

        Store entityStore = world.getEntityStore().getStore();
        Holder[] holders = entityChunkComponent.takeEntityHolders();
        if (holders == null) {
            return;
        }

        int holderCount = holders.length;
        for (int i = holderCount - 1; i >= 0; --i) {
            Holder holder = holders[i];
            Archetype archetype = holder.getArchetype();
            if (archetype == null) {
                holders[i] = holders[--holderCount];
                holders[holderCount] = holder;
                continue;
            }

            if (archetype.isEmpty()) {
                refixes$LOGGER.atSevere().log("Empty archetype entity holder: %s (#%d)", holder, i);
                holders[i] = holders[--holderCount];
                holders[holderCount] = holder;
                worldChunkComponent.markNeedsSaving();
                continue;
            }

            if (archetype.count() == 1 && archetype.contains(Nameplate.getComponentType())) {
                refixes$LOGGER.atSevere().log("Nameplate only entity holder: %s (#%d)", holder, i);
                holders[i] = holders[--holderCount];
                holders[holderCount] = holder;
                worldChunkComponent.markNeedsSaving();
                continue;
            }

            TransformComponent transformComponent =
                    (TransformComponent) holder.getComponent(TransformComponent.getComponentType());
            if (transformComponent == null) {
                refixes$LOGGER.atWarning().log(
                        "EntityChunkLoadingSystem#onComponentRemoved(): skipping entity holder with null TransformComponent (chunk ref: %s)",
                        ref);
                holders[i] = holders[--holderCount];
                holders[holderCount] = holder;
                worldChunkComponent.markNeedsSaving();
                continue;
            }

            transformComponent.setChunkLocation(ref, worldChunkComponent);
        }

        Ref[] refs = entityStore.addEntities(holders, 0, holderCount, AddReason.LOAD);
        for (int i = 0; i < refs.length && refs[i].isValid(); ++i) {
            entityChunkComponent.loadEntityReference(refs[i]);
        }
    }
}
