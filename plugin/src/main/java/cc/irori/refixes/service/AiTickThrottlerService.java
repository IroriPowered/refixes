package cc.irori.refixes.service;

import cc.irori.refixes.config.impl.AiTickThrottlerConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.StepComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Distance-based LOD for AI entity ticking
 *
 * Freezes distant NPCs and reduces their tick rate based on chunk proximity to the nearest player
 * ≤ 1 chunk — full AI tick rate
 * ≤ 2 chunks — mid tick rate (0.2s)
 * ≤ 4 chunks — far tick rate (0.5s)
 * > 4 chunks — very far tick rate (1.0s)
 */
public class AiTickThrottlerService {

    private static final HytaleLogger LOGGER = Logs.logger();

    private ComponentType<EntityStore, ?> npcType;
    private ComponentType<EntityStore, TransformComponent> transformType;
    private ComponentType<EntityStore, UUIDComponent> uuidType;
    private ComponentType<EntityStore, Frozen> frozenType;
    private ComponentType<EntityStore, StepComponent> stepType;
    private ComponentType<EntityStore, Player> playerType;
    private Query<EntityStore> npcQuery;

    private final Map<String, WorldState> worldStates = new ConcurrentHashMap<>();
    private ScheduledFuture<?> task;

    public void registerService() {
        if (AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.CLEANUP_FROZEN_ON_START)) {
            // Sweep orphaned Frozen/StepComponent from a prior run
            if (resolveComponentTypes()) {
                for (World world : Universe.get().getWorlds().values()) {
                    world.execute(() -> sweepOrphaned(world));
                }
            }
        }

        if (!AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.ENABLED)) {
            return;
        }

        int intervalMs = Math.max(20, AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.UPDATE_INTERVAL_MS));
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        throttle();
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error in AI tick throttler");
                    }
                },
                5000,
                intervalMs,
                TimeUnit.MILLISECONDS);
    }

    public void unregisterService() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        // Unfreeze all entities we froze before clearing state
        if (resolveComponentTypes()) {
            Map<String, World> worlds = Universe.get().getWorlds();
            for (Map.Entry<String, WorldState> ws : worldStates.entrySet()) {
                World world = worlds.get(ws.getKey());
                if (world != null) {
                    WorldState state = ws.getValue();
                    world.execute(() -> unfreezeAll(world, state));
                }
            }
        }
        worldStates.clear();
    }

    private void unfreezeAll(World world, WorldState state) {
        Store<EntityStore> store = world.getEntityStore().getStore();
        store.forEachEntityParallel(npcQuery, (index, archetypeChunk, commandBuffer) -> {
            UUIDComponent uuid = archetypeChunk.getComponent(index, uuidType);
            if (uuid == null) return;
            AiLodEntry entry = state.entries.get(uuid.getUuid());
            if (entry != null && entry.forcedFrozen) {
                Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                commandBuffer.tryRemoveComponent(ref, frozenType);
                commandBuffer.tryRemoveComponent(ref, stepType);
            }
        });
    }

    // Removes Frozen and StepComponent from all NPCs not covered by the global freeze

    private void sweepOrphaned(World world) {
        if (world.getWorldConfig().isAllNPCFrozen()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        store.forEachEntityParallel(npcQuery, (index, archetypeChunk, commandBuffer) -> {
            if (playerType != null && archetypeChunk.getArchetype().contains(playerType)) {
                return;
            }
            boolean frozen = archetypeChunk.getComponent(index, frozenType) != null;
            boolean hasStep = archetypeChunk.getComponent(index, stepType) != null;
            if (frozen || hasStep) {
                Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                commandBuffer.tryRemoveComponent(ref, frozenType);
                commandBuffer.tryRemoveComponent(ref, stepType);
            }
        });
        LOGGER.atInfo().log("[%s] Swept orphaned Frozen/StepComponent from NPCs", world.getName());
    }

    private void throttle() {
        AiTickThrottlerConfig cfg = AiTickThrottlerConfig.get();
        if (!cfg.getValue(AiTickThrottlerConfig.ENABLED)) {
            if (!worldStates.isEmpty() && resolveComponentTypes()) {
                Map<String, World> worlds = Universe.get().getWorlds();
                for (Map.Entry<String, WorldState> ws : worldStates.entrySet()) {
                    World world = worlds.get(ws.getKey());
                    if (world != null) {
                        WorldState state = ws.getValue();
                        world.execute(() -> unfreezeAll(world, state));
                    }
                }
                worldStates.clear();
            }
            return;
        }

        Map<String, World> worlds = Universe.get().getWorlds();
        worldStates.keySet().removeIf(name -> !worlds.containsKey(name));

        for (World world : worlds.values()) {
            world.execute(() -> processWorld(world, cfg));
        }
    }

    private void processWorld(World world, AiTickThrottlerConfig cfg) {
        if (!resolveComponentTypes()) {
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        WorldState state = worldStates.computeIfAbsent(world.getName(), _k -> new WorldState());

        // Precompute player chunk positions
        List<int[]> playerChunks = collectPlayerChunkPositions(world.getPlayerRefs());
        long now = System.nanoTime();

        int nearChunks = Math.max(0, cfg.getValue(AiTickThrottlerConfig.NEAR_CHUNKS));
        int midChunks = Math.max(nearChunks, cfg.getValue(AiTickThrottlerConfig.MID_CHUNKS));
        int farChunks = Math.max(midChunks, cfg.getValue(AiTickThrottlerConfig.FAR_CHUNKS));

        store.forEachEntityParallel(npcQuery, (index, archetypeChunk, commandBuffer) -> {
            // Skip player entities
            if (playerType != null && archetypeChunk.getArchetype().contains(playerType)) {
                return;
            }

            TransformComponent transform = archetypeChunk.getComponent(index, transformType);
            UUIDComponent uuid = archetypeChunk.getComponent(index, uuidType);
            if (transform == null || uuid == null) {
                return;
            }

            // Compute chunk distance to nearest player
            int entityChunkX = ChunkUtil.chunkCoordinate(transform.getPosition().getX());
            int entityChunkZ = ChunkUtil.chunkCoordinate(transform.getPosition().getZ());
            int chunkDist = closestPlayerChunkDistance(entityChunkX, entityChunkZ, playerChunks);
            UUID entityId = uuid.getUuid();

            double intervalSec = computeInterval(chunkDist, nearChunks, midChunks, farChunks, cfg);

            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            AiLodEntry entry = state.entries.get(entityId);
            boolean frozen = archetypeChunk.getComponent(index, frozenType) != null;
            boolean hasStep = archetypeChunk.getComponent(index, stepType) != null;

            // If near enough, remove throttling
            if (intervalSec <= 0.0) {
                if (frozen && (entry != null || !world.getWorldConfig().isAllNPCFrozen())) {
                    commandBuffer.tryRemoveComponent(ref, frozenType);
                    commandBuffer.tryRemoveComponent(ref, stepType);
                }
                state.entries.remove(entityId);
                return;
            }

            // Skip entities frozen by the global world freeze
            if (entry == null && frozen && world.getWorldConfig().isAllNPCFrozen()) {
                return;
            }

            if (entry == null) {
                entry = new AiLodEntry();
                state.entries.put(entityId, entry);
            }

            if (!entry.forcedFrozen) {
                commandBuffer.ensureComponent(ref, frozenType);
                entry.forcedFrozen = true;
            }

            float minTick = cfg.getValue(AiTickThrottlerConfig.MIN_TICK_SECONDS);
            long intervalNanos = (long) (Math.max(minTick, intervalSec) * 1_000_000_000.0);
            if (entry.intervalNanos != intervalNanos) {
                entry.intervalNanos = intervalNanos;
                entry.nextTickNanos = now;
            }

            if (now >= entry.nextTickNanos) {
                commandBuffer.putComponent(
                        ref, stepType, new StepComponent((float) ((double) intervalNanos / 1_000_000_000.0)));
                entry.nextTickNanos = now + intervalNanos;
            }
        });
    }

    private static double computeInterval(
            int chunkDist, int nearChunks, int midChunks, int farChunks, AiTickThrottlerConfig cfg) {
        if (chunkDist <= nearChunks) {
            return 0.0;
        }
        if (chunkDist <= midChunks) {
            return cfg.getValue(AiTickThrottlerConfig.MID_TICK_SECONDS);
        }
        if (chunkDist <= farChunks) {
            return cfg.getValue(AiTickThrottlerConfig.FAR_TICK_SECONDS);
        }
        return cfg.getValue(AiTickThrottlerConfig.VERY_FAR_TICK_SECONDS);
    }

    private static List<int[]> collectPlayerChunkPositions(Collection<PlayerRef> players) {
        List<int[]> positions = new ArrayList<>();
        if (players == null || players.isEmpty()) {
            return positions;
        }
        for (PlayerRef player : players) {
            if (player == null) continue;
            Transform transform = player.getTransform();
            if (transform == null) continue;
            int chunkX = ChunkUtil.chunkCoordinate(transform.getPosition().getX());
            int chunkZ = ChunkUtil.chunkCoordinate(transform.getPosition().getZ());
            positions.add(new int[] {chunkX, chunkZ});
        }
        return positions;
    }

    private static int closestPlayerChunkDistance(int entityChunkX, int entityChunkZ, List<int[]> playerChunks) {
        if (playerChunks.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        int minDist = Integer.MAX_VALUE;
        for (int[] pc : playerChunks) {
            int dist = Math.max(Math.abs(entityChunkX - pc[0]), Math.abs(entityChunkZ - pc[1]));
            if (dist < minDist) {
                minDist = dist;
                if (dist == 0) return 0;
            }
        }
        return minDist;
    }

    @SuppressWarnings("unchecked")
    private boolean resolveComponentTypes() {
        if (npcType != null && transformType != null && uuidType != null && frozenType != null && stepType != null) {
            return true;
        }
        try {
            if (npcType == null) npcType = EntityModule.get().getNPCMarkerComponentType();
            if (transformType == null) transformType = TransformComponent.getComponentType();
            if (uuidType == null) uuidType = UUIDComponent.getComponentType();
            if (frozenType == null) frozenType = Frozen.getComponentType();
            if (stepType == null) stepType = StepComponent.getComponentType();
            if (playerType == null) playerType = Player.getComponentType();

            if (npcQuery == null) {
                npcQuery = Query.and(npcType, transformType);
            }
        } catch (Throwable t) {
            return false;
        }
        return npcType != null && transformType != null && uuidType != null && frozenType != null && stepType != null;
    }

    private static final class WorldState {
        final Map<UUID, AiLodEntry> entries = new ConcurrentHashMap<>();
    }

    private static final class AiLodEntry {
        boolean forcedFrozen;
        long intervalNanos;
        long nextTickNanos;
    }
}
