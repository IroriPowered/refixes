package cc.irori.refixes.listener;

import cc.irori.refixes.compat.BlackboxBridge;
import cc.irori.refixes.config.impl.ListenerConfig;
import cc.irori.refixes.early.duck.UnknownFluidScannable;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class UnknownBlockCleaner {

    private static final HytaleLogger LOGGER = Logs.logger();
    private static final Queue<PendingSection> pendingSections = new ArrayDeque<>();
    private static ScheduledFuture<?> cleanerTask;
    private static volatile long generation;
    private static volatile boolean running;

    private UnknownBlockCleaner() {}

    public static synchronized void registerEvents(JavaPlugin plugin) {
        shutdown();
        long currentGeneration = generation;
        running = true;
        plugin.getChunkStoreRegistry().registerSystem(new SectionAddedSystem(currentGeneration));
        plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            if (isRunning(currentGeneration)) {
                cleanPlayerInventory(event);
            }
        });
        int intervalMs = Math.max(20, ListenerConfig.get().getValue(ListenerConfig.UNKNOWN_BLOCK_CLEANER_INTERVAL_MS));
        cleanerTask = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        drainQueue(currentGeneration);
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error in unknown block cleaner");
                    }
                },
                1000,
                intervalMs,
                TimeUnit.MILLISECONDS);
    }

    public static synchronized void shutdown() {
        running = false;
        generation++;
        if (cleanerTask != null) {
            cleanerTask.cancel(false);
            cleanerTask = null;
        }
        pendingSections.clear();
    }

    private static boolean isRunning(long currentGeneration) {
        return running && generation == currentGeneration;
    }

    private static synchronized void enqueue(PendingSection section, long currentGeneration) {
        if (isRunning(currentGeneration)) {
            pendingSections.add(section);
        }
    }

    private static void drainQueue(long currentGeneration) {
        Map<World, List<PendingSection>> byWorld = new HashMap<>();
        synchronized (UnknownBlockCleaner.class) {
            if (!isRunning(currentGeneration)) {
                return;
            }
            PendingSection section;
            while ((section = pendingSections.poll()) != null) {
                byWorld.computeIfAbsent(section.world(), ignored -> new ArrayList<>())
                        .add(section);
            }
        }
        for (Map.Entry<World, List<PendingSection>> entry : byWorld.entrySet()) {
            if (!isRunning(currentGeneration)) {
                return;
            }
            if (!entry.getKey().isAlive()) {
                continue;
            }
            try {
                entry.getKey().execute(() -> processSections(entry.getValue(), currentGeneration));
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log(
                        "Unable to schedule unknown section cleanup in world '%s'",
                        entry.getKey().getName());
            }
        }
    }

    private static void processSections(List<PendingSection> sections, long currentGeneration) {
        int budgetMs = Math.max(1, ListenerConfig.get().getValue(ListenerConfig.UNKNOWN_BLOCK_CLEANER_BUDGET_MS));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs);
        for (int i = 0; i < sections.size(); i++) {
            if (!isRunning(currentGeneration)) {
                return;
            }
            PendingSection section = sections.get(i);
            if (!section.world().isAlive()) {
                return;
            }
            try {
                cleanSection(section.reference());
            } catch (Exception e) {
                LOGGER.atWarning().withCause(e).log(
                        "Error cleaning section in world '%s'", section.world().getName());
            }
            if (System.nanoTime() >= deadline) {
                for (int j = i + 1; j < sections.size(); j++) {
                    enqueue(sections.get(j), currentGeneration);
                }
                return;
            }
        }
    }

    private static void cleanSection(Ref<ChunkStore> sectionRef) {
        if (!sectionRef.isValid()) {
            return;
        }
        Store<ChunkStore> store = sectionRef.getStore();
        ChunkSection section = store.getComponent(sectionRef, ChunkSection.getComponentType());
        if (section == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Map<String, Integer> removedCounts = new HashMap<>();
        String[] exclude = ListenerConfig.get().getValue(ListenerConfig.UNKNOWN_BLOCK_CLEANER_EXCLUDE);
        BlockComponentSection blockComponents =
                store.getComponent(sectionRef, BlockComponentSection.getComponentType());
        cleanBlocks(sectionRef, section, blockComponents, removedCounts, exclude);
        cleanFluids(store, sectionRef, section, removedCounts, exclude);
        cleanContainers(store, section, blockComponents, removedCounts, exclude);
        if (!removedCounts.isEmpty()) {
            section.markNeedsSaving();
            Ref<ChunkStore> columnRef = section.getChunkColumnReference();
            if (columnRef != null && columnRef.isValid()) {
                WorldChunk chunk = store.getComponent(columnRef, WorldChunk.getComponentType());
                if (chunk != null) {
                    chunk.markNeedsSaving();
                }
            }
            int total =
                    removedCounts.values().stream().mapToInt(Integer::intValue).sum();
            LOGGER.atInfo().log(
                    "Cleaned %d unknown entries (%d types) from section (%d, %d, %d) in world '%s': %s",
                    total,
                    removedCounts.size(),
                    section.getX(),
                    section.getY(),
                    section.getZ(),
                    world.getName(),
                    removedCounts);
            BlackboxBridge.count("UnknownBlockCleaner removed", total);
        }
    }

    private static void cleanBlocks(
            Ref<ChunkStore> sectionRef,
            ChunkSection section,
            BlockComponentSection blockComponents,
            Map<String, Integer> removedCounts,
            String[] exclude) {
        Store<ChunkStore> store = sectionRef.getStore();
        BlockSection blocks = store.getComponent(sectionRef, BlockSection.getComponentType());
        if (blocks == null || blocks.isSolidAir() || !sectionHasUnknown(blocks)) {
            return;
        }
        Ref<ChunkStore> columnRef = section.getChunkColumnReference();
        if (columnRef == null || !columnRef.isValid()) {
            return;
        }
        BlockChunk blockChunk = store.getComponent(columnRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            return;
        }
        ChunkStore chunkStore = store.getExternalData();
        BlockTypeAssetMap<String, BlockType> assetMap = BlockType.getAssetMap();
        int startX = section.getX() << ChunkUtil.BITS;
        int startY = section.getY() << ChunkUtil.BITS;
        int startZ = section.getZ() << ChunkUtil.BITS;
        for (int x = 0; x < ChunkUtil.SIZE; x++) {
            for (int z = 0; z < ChunkUtil.SIZE; z++) {
                for (int y = 0; y < ChunkUtil.SIZE; y++) {
                    try {
                        BlockType blockType = assetMap.getAsset(blocks.get(x, y, z));
                        if (blockType != null
                                && blockType.isUnknown()
                                && !isExcluded(blockType.getId(), exclude)
                                && BlockOperations.setBlock(
                                        chunkStore,
                                        sectionRef,
                                        section,
                                        blocks,
                                        blockComponents,
                                        blockChunk,
                                        startX + x,
                                        startY + y,
                                        startZ + z,
                                        BlockType.EMPTY_ID,
                                        BlockType.EMPTY,
                                        RotationTuple.NONE_INDEX,
                                        FillerBlockUtil.NO_FILLER,
                                        SetBlockSettings.NONE)) {
                            section.markNeedsSaving();
                            removedCounts.merge(blockType.getId(), 1, Integer::sum);
                        }
                    } catch (Throwable e) {
                        LOGGER.atWarning().withCause(e).log(
                                "Error cleaning block at (%d, %d, %d) in world '%s'",
                                startX + x,
                                startY + y,
                                startZ + z,
                                chunkStore.getWorld().getName());
                    }
                }
            }
        }
    }

    private static void cleanFluids(
            Store<ChunkStore> store,
            Ref<ChunkStore> sectionRef,
            ChunkSection section,
            Map<String, Integer> removedCounts,
            String[] exclude) {
        if (!ListenerConfig.get().getValue(ListenerConfig.UNKNOWN_BLOCK_CLEANER_SCAN_FLUIDS)) {
            return;
        }
        FluidSection fluids = store.getComponent(sectionRef, FluidSection.getComponentType());
        if (fluids == null
                || fluids.isEmpty()
                || (fluids instanceof UnknownFluidScannable scannable && !scannable.refixes$hasUnknownFluid())) {
            return;
        }
        for (int x = 0; x < ChunkUtil.SIZE; x++) {
            for (int z = 0; z < ChunkUtil.SIZE; z++) {
                for (int y = 0; y < ChunkUtil.SIZE; y++) {
                    try {
                        Fluid fluid = fluids.getFluid(x, y, z);
                        if (fluid != null
                                && fluid.isUnknown()
                                && !isExcluded(fluid.getId(), exclude)
                                && fluids.setFluid(x, y, z, Fluid.EMPTY, (byte) 0)) {
                            section.markNeedsSaving();
                            removedCounts.merge("fluid/" + fluid.getId(), 1, Integer::sum);
                        }
                    } catch (Throwable e) {
                        LOGGER.atWarning().withCause(e).log(
                                "Error cleaning fluid at section-local (%d, %d, %d) in world '%s'",
                                x, y, z, store.getExternalData().getWorld().getName());
                    }
                }
            }
        }
    }

    private static void cleanContainers(
            Store<ChunkStore> store,
            ChunkSection section,
            BlockComponentSection blockComponents,
            Map<String, Integer> removedCounts,
            String[] exclude) {
        if (!ListenerConfig.get().getValue(ListenerConfig.UNKNOWN_BLOCK_CLEANER_SCAN_CONTAINERS)
                || blockComponents == null) {
            return;
        }
        String worldName = store.getExternalData().getWorld().getName();
        for (var entry : blockComponents.getBlockReferences().short2ReferenceEntrySet()) {
            Ref<ChunkStore> blockRef = entry.getValue();
            if (blockRef == null || !blockRef.isValid()) {
                continue;
            }
            ItemContainerBlock containerBlock = store.getComponent(blockRef, ItemContainerBlock.getComponentType());
            if (containerBlock != null
                    && cleanItemContainer(containerBlock.getItemContainer(), removedCounts, worldName, exclude)) {
                blockComponents.markBlockNeedsSaving(entry.getShortKey());
                section.markNeedsSaving();
            }
        }
        for (var entry : blockComponents.getBlockHolders().short2ObjectEntrySet()) {
            ItemContainerBlock containerBlock = entry.getValue().getComponent(ItemContainerBlock.getComponentType());
            if (containerBlock != null
                    && cleanItemContainer(containerBlock.getItemContainer(), removedCounts, worldName, exclude)) {
                blockComponents.markBlockNeedsSaving(entry.getShortKey());
                section.markNeedsSaving();
            }
        }
    }

    private static void cleanPlayerInventory(AddPlayerToWorldEvent event) {
        if (!ListenerConfig.get().getValue(ListenerConfig.UNKNOWN_BLOCK_CLEANER_SCAN_PLAYER_INVENTORIES)) {
            return;
        }
        World world = event.getWorld();
        Holder<EntityStore> holder = event.getHolder();
        if (world == null || holder == null) {
            return;
        }
        Map<String, Integer> removedCounts = new HashMap<>();
        String[] exclude = ListenerConfig.get().getValue(ListenerConfig.UNKNOWN_BLOCK_CLEANER_EXCLUDE);
        for (ComponentType<EntityStore, ? extends InventoryComponent> type : InventoryComponent.EVERYTHING) {
            InventoryComponent inventory = holder.getComponent(type);
            if (inventory != null) {
                cleanItemContainer(inventory.getInventory(), removedCounts, world.getName(), exclude);
            }
        }
        if (!removedCounts.isEmpty()) {
            int total =
                    removedCounts.values().stream().mapToInt(Integer::intValue).sum();
            LOGGER.atInfo().log(
                    "Cleaned %d unknown items (%d types) from a player's inventory in world '%s': %s",
                    total, removedCounts.size(), world.getName(), removedCounts);
            BlackboxBridge.count("UnknownBlockCleaner removed", total);
        }
    }

    private static boolean cleanItemContainer(
            ItemContainer container, Map<String, Integer> removedCounts, String worldName, String[] exclude) {
        if (container == null) {
            return false;
        }
        boolean changed = false;
        short capacity = container.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            try {
                ItemStack stack = container.getItemStack(slot);
                if (stack != null
                        && !stack.isEmpty()
                        && stack.getItem() == Item.UNKNOWN
                        && !isExcluded(stack.getItemId(), exclude)
                        && container.setItemStackForSlot(slot, ItemStack.EMPTY).succeeded()) {
                    changed = true;
                    removedCounts.merge("item/" + stack.getItemId(), Math.max(1, stack.getQuantity()), Integer::sum);
                }
            } catch (Throwable t) {
                LOGGER.atWarning().withCause(t).log("Error cleaning item slot %d in world '%s'", slot, worldName);
            }
        }
        return changed;
    }

    private static boolean isExcluded(String id, String[] exclude) {
        if (id == null || exclude == null || exclude.length == 0) {
            return false;
        }
        for (String prefix : exclude) {
            if (prefix != null && !prefix.isEmpty() && id.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sectionHasUnknown(BlockSection section) {
        BlockTypeAssetMap<String, BlockType> assetMap = BlockType.getAssetMap();
        for (int blockId : section.values()) {
            if (blockId == 0) {
                continue;
            }
            BlockType blockType = assetMap.getAsset(blockId);
            if (blockType == null || blockType.isUnknown()) {
                return true;
            }
        }
        return false;
    }

    private record PendingSection(World world, Ref<ChunkStore> reference) {}

    public static final class SectionAddedSystem extends RefSystem<ChunkStore> {
        private final long currentGeneration;

        private SectionAddedSystem(long currentGeneration) {
            this.currentGeneration = currentGeneration;
        }

        @Override
        public void onEntityAdded(
                Ref<ChunkStore> ref,
                AddReason reason,
                Store<ChunkStore> store,
                CommandBuffer<ChunkStore> commandBuffer) {
            enqueue(new PendingSection(store.getExternalData().getWorld(), ref), currentGeneration);
        }

        @Override
        public void onEntityRemove(
                Ref<ChunkStore> ref,
                RemoveReason reason,
                Store<ChunkStore> store,
                CommandBuffer<ChunkStore> commandBuffer) {}

        @Override
        public Query<ChunkStore> getQuery() {
            return ChunkSection.getComponentType();
        }
    }
}
