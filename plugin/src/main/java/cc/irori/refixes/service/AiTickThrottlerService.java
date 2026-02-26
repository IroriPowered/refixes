package cc.irori.refixes.service;

import cc.irori.refixes.component.TickThrottled;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distance-based LOD for AI entity ticking
 *
 * Freezes distant NPCs and reduces their tick rate based on chunk proximity to the nearest player
 * ≤ 3 chunks (~48 blocks) — full AI tick rate
 * ≤ 5 chunks (~80 blocks) — mid tick rate (0.2s)
 * ≤ 8 chunks (~128 blocks) — far tick rate (0.5s)
 * > 8 chunks — very far tick rate (1.0s)
 */
public class AiTickThrottlerService {

    private static final HytaleLogger LOGGER = Logs.logger();

    // Cached component types — resolved once via resolveComponentTypes()
    private ComponentType<EntityStore, ?> npcType;
    private ComponentType<EntityStore, TransformComponent> transformType;
    private ComponentType<EntityStore, UUIDComponent> uuidType;
    private ComponentType<EntityStore, Frozen> frozenType;
    private ComponentType<EntityStore, StepComponent> stepType;
    private ComponentType<EntityStore, TickThrottled> tickThrottledType;
    private ComponentType<EntityStore, Player> playerType;
    private Query<EntityStore> npcQuery;

    private final Map<String, WorldState> worldStates = new ConcurrentHashMap<>();
    private ScheduledFuture<?> task;

    public void registerService() {
        int intervalMs = Math.max(20, AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.UPDATE_INTERVAL_MS));
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        if (resolveComponentTypes()) {
                            throttle();
                        }
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
        unfreezeAllWorld();
        worldStates.clear();
    }

    private void unfreezeAllWorld() {
        Map<String, World> worlds = Universe.get().getWorlds();
        for (Map.Entry<String, WorldState> ws : worldStates.entrySet()) {
            World world = worlds.get(ws.getKey());
            if (world != null) {
                WorldState state = ws.getValue();
                world.execute(() -> unfreezeAll(world, state));
            }
        }
    }

    private void unfreezeAll(World world, WorldState state) {
        if (!resolveComponentTypes()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        store.forEachEntityParallel(npcQuery, (index, archetypeChunk, commandBuffer) -> {
            TickThrottled tickThrottled = archetypeChunk.getComponent(index, tickThrottledType);
            if (tickThrottled == null) {
                return;
            }

            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            commandBuffer.tryRemoveComponent(ref, frozenType);
            commandBuffer.tryRemoveComponent(ref, stepType);
            commandBuffer.tryRemoveComponent(ref, tickThrottledType);
        });
    }

    private void throttle() {
        AiTickThrottlerConfig cfg = AiTickThrottlerConfig.get();

        Map<String, World> worlds = Universe.get().getWorlds();
        worldStates.keySet().removeIf(name -> !worlds.containsKey(name));

        for (World world : worlds.values()) {
            world.execute(() -> processWorld(world, cfg));
        }
    }

    private void processWorld(World world, AiTickThrottlerConfig cfg) {
        if (world.getWorldConfig().isAllNPCFrozen()) {
            return;
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        WorldState state = worldStates.computeIfAbsent(world.getName(), _k -> new WorldState());

        // Precompute player chunk positions
        List<int[]> playerChunks = collectPlayerChunkPositions(world.getPlayerRefs());

        // No players online: freeze all NPCs once, then skip subsequent cycles
        if (playerChunks.isEmpty()) {
            if (!state.frozenWithoutPlayers) {
                freezeAllNpcs(store);
                state.frozenWithoutPlayers = true;
            }
            return;
        }
        state.frozenWithoutPlayers = false;

        long now = System.nanoTime();

        int nearChunks = Math.max(0, cfg.getValue(AiTickThrottlerConfig.NEAR_CHUNKS));
        int midChunks = Math.max(nearChunks, cfg.getValue(AiTickThrottlerConfig.MID_CHUNKS));
        int farChunks = Math.max(midChunks, cfg.getValue(AiTickThrottlerConfig.FAR_CHUNKS));

        int hysteresis = Math.max(0, cfg.getValue(AiTickThrottlerConfig.ACTIVATION_HYSTERESIS_CHUNKS));
        int maxUnfreezes = Math.max(1, cfg.getValue(AiTickThrottlerConfig.MAX_UNFREEZES_PER_TICK));
        AtomicInteger unfreezeCount = new AtomicInteger(0);

        float minTick = cfg.getValue(AiTickThrottlerConfig.MIN_TICK_SECONDS);

        // Pre-compute StepComponent instances for each tier to avoid per-entity allocation
        float midSec = Math.max(minTick, cfg.getValue(AiTickThrottlerConfig.MID_TICK_SECONDS));
        float farSec = Math.max(minTick, cfg.getValue(AiTickThrottlerConfig.FAR_TICK_SECONDS));
        float veryFarSec = Math.max(minTick, cfg.getValue(AiTickThrottlerConfig.VERY_FAR_TICK_SECONDS));
        StepComponent midStep = new StepComponent(midSec);
        StepComponent farStep = new StepComponent(farSec);
        StepComponent veryFarStep = new StepComponent(veryFarSec);

        // Reuse seen set to avoid allocating a new ConcurrentHashMap each cycle
        state.seen.clear();

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
            state.seen.add(entityId);

            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);

            boolean frozen = archetypeChunk.getComponent(index, frozenType) != null;
            boolean throttled = archetypeChunk.getComponent(index, tickThrottledType) != null;

            // Don't mess around if the entity is already frozen without our throttle marker
            if (frozen && !throttled) {
                return;
            }

            // Already throttled → unfreeze at nearChunks (tight)
            // Not throttled → freeze at nearChunks + hysteresis (wider)
            double intervalSec;
            if (throttled) {
                intervalSec = computeInterval(chunkDist, nearChunks, midChunks, farChunks, cfg);
            } else {
                intervalSec = computeInterval(
                        chunkDist, nearChunks + hysteresis, midChunks + hysteresis, farChunks + hysteresis, cfg);
            }

            // If near enough, remove throttling
            if (intervalSec <= 0.0) {
                if (throttled && unfreezeCount.incrementAndGet() <= maxUnfreezes) {
                    commandBuffer.tryRemoveComponent(ref, frozenType);
                    commandBuffer.tryRemoveComponent(ref, stepType);
                    commandBuffer.tryRemoveComponent(ref, tickThrottledType);
                    state.entries.remove(entityId);
                }
                return;
            }

            AiLodEntry entry = state.entries.computeIfAbsent(uuid.getUuid(), _k -> new AiLodEntry());
            if (!throttled) {
                commandBuffer.ensureComponent(ref, frozenType);
                commandBuffer.ensureComponent(ref, tickThrottledType);
            }

            long intervalNanos = (long) (Math.max(minTick, intervalSec) * 1_000_000_000.0);
            if (entry.intervalNanos != intervalNanos) {
                entry.intervalNanos = intervalNanos;
                entry.nextTickNanos = now;
            }

            if (now >= entry.nextTickNanos) {
                // Use pre-computed step for the matching tier
                StepComponent step;
                if (chunkDist <= midChunks) {
                    step = midStep;
                } else if (chunkDist <= farChunks) {
                    step = farStep;
                } else {
                    step = veryFarStep;
                }
                commandBuffer.putComponent(ref, stepType, step);
                entry.nextTickNanos = now + intervalNanos;
            }
        });

        // Prune entries for entities no longer in the world
        state.entries.keySet().retainAll(state.seen);
    }

    private void freezeAllNpcs(Store<EntityStore> store) {
        store.forEachEntityParallel(npcQuery, (index, archetypeChunk, commandBuffer) -> {
            if (playerType != null && archetypeChunk.getArchetype().contains(playerType)) {
                return;
            }
            boolean frozen = archetypeChunk.getComponent(index, frozenType) != null;
            boolean throttled = archetypeChunk.getComponent(index, tickThrottledType) != null;
            if (frozen && !throttled) {
                return;
            }
            if (!frozen) {
                Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
                commandBuffer.ensureComponent(ref, frozenType);
                commandBuffer.ensureComponent(ref, tickThrottledType);
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

    private boolean resolveComponentTypes() {
        if (npcType != null
                && transformType != null
                && uuidType != null
                && frozenType != null
                && stepType != null
                && tickThrottledType != null) {
            return true;
        }
        try {
            if (npcType == null) npcType = EntityModule.get().getNPCMarkerComponentType();
            if (transformType == null) transformType = TransformComponent.getComponentType();
            if (uuidType == null) uuidType = UUIDComponent.getComponentType();
            if (frozenType == null) frozenType = Frozen.getComponentType();
            if (stepType == null) stepType = StepComponent.getComponentType();
            if (tickThrottledType == null) tickThrottledType = TickThrottled.getComponentType();
            if (playerType == null) playerType = Player.getComponentType();

            if (npcQuery == null) {
                npcQuery = Query.and(npcType, transformType);
            }
        } catch (Throwable t) {
            return false;
        }
        return npcType != null
                && transformType != null
                && uuidType != null
                && frozenType != null
                && stepType != null
                && tickThrottledType != null;
    }

    private static final class WorldState {
        final Map<UUID, AiLodEntry> entries = new ConcurrentHashMap<>();
        final Set<UUID> seen = ConcurrentHashMap.newKeySet();
        boolean frozenWithoutPlayers;
    }

    private static final class AiLodEntry {
        long intervalNanos;
        long nextTickNanos;
    }
}
