package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.Dirty;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.chunk.section.EntitySection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.flock.StoredFlock;
import com.hypixel.hytale.server.spawning.spawnmarkers.SpawnMarkerSystems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SpawnMarkerSystems.Ticking.class)
public class MixinSpawnMarkerTicking {

    @WrapOperation(
            method = "tick",
            at = {
                @At(
                        value = "INVOKE",
                        target = "Lcom/hypixel/hytale/component/CommandBuffer;run(Ljava/util/function/Consumer;)V",
                        ordinal = 0),
                @At(
                        value = "INVOKE",
                        target = "Lcom/hypixel/hytale/component/CommandBuffer;run(Ljava/util/function/Consumer;)V",
                        ordinal = 1)
            },
            require = 2)
    private void refixes$saveStoredFlockChange(
            CommandBuffer<EntityStore> commandBuffer,
            Consumer<Store<EntityStore>> change,
            Operation<Void> original,
            @Local(argsOnly = true) int index,
            @Local(argsOnly = true) ArchetypeChunk<EntityStore> archetypeChunk) {
        Ref<EntityStore> markerRef = archetypeChunk.getReferenceTo(index);
        original.call(commandBuffer, (Consumer<Store<EntityStore>>) store -> {
            change.accept(store);
            if (!markerRef.isValid()) return;
            Dirty dirty = store.getComponent(markerRef, Dirty.getComponentType());
            if (dirty != null) dirty.forceMarkDirty();
        });
    }

    @WrapOperation(
            method = "lambda$tick$1",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/flock/StoredFlock;storeNPCs(Ljava/util/List;Lcom/hypixel/hytale/component/Store;)V"),
            require = 1)
    private static void refixes$saveStoredMemberSections(
            StoredFlock flock, List<Ref<EntityStore>> members, Store<EntityStore> store, Operation<Void> original) {
        List<Ref<ChunkStore>> sections = new ArrayList<>(members.size());
        for (Ref<EntityStore> member : members) {
            if (!member.isValid()) continue;
            TransformComponent transform = store.getComponent(member, TransformComponent.getComponentType());
            if (transform != null && transform.getSectionRef() != null) sections.add(transform.getSectionRef());
        }
        original.call(flock, members, store);
        if (sections.isEmpty()) return;
        Store<ChunkStore> chunkStore =
                store.getExternalData().getWorld().getChunkStore().getStore();
        for (Ref<ChunkStore> section : sections) {
            if (!section.isValid()) continue;
            EntitySection entitySection = chunkStore.getComponent(section, EntitySection.getComponentType());
            if (entitySection != null) entitySection.markNeedsSaving();
        }
    }
}
