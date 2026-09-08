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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection$EntitySectionLoadingSystem")
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
        if (world == null) {
            return;
        }

        EntitySection entitySection = (EntitySection) store.getComponent(ref, EntitySection.getComponentType());
        if (entitySection == null) {
            return;
        }

        Store entityStore = world.getEntityStore().getStore();
        Holder[] holders = entitySection.takeEntityHolders();
        if (holders == null) {
            return;
        }

        int holderCount = holders.length;
        for (int i = holderCount - 1; i >= 0; --i) {
            Holder holder = holders[i];
            Archetype archetype = holder == null ? null : holder.getArchetype();
            if (archetype == null) {
                holders[i] = holders[--holderCount];
                holders[holderCount] = holder;
                entitySection.markNeedsSaving();
                continue;
            }

            if (archetype.isEmpty()) {
                refixes$LOGGER.atSevere().log("Empty archetype entity holder: %s (#%d)", holder, i);
                holders[i] = holders[--holderCount];
                holders[holderCount] = holder;
                entitySection.markNeedsSaving();
                continue;
            }

            TransformComponent transformComponent =
                    (TransformComponent) holder.getComponent(TransformComponent.getComponentType());
            if (transformComponent != null) {
                transformComponent.setSectionLocation(ref);
            }
        }

        Ref[] refs = entityStore.addEntities(holders, 0, holderCount, AddReason.LOAD);
        for (int i = 0; i < refs.length; ++i) {
            if (!refs[i].isValid()) {
                break;
            }
            entitySection.loadEntityReference(refs[i]);
        }
    }
}
