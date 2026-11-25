package cc.irori.refixes.service;

import cc.irori.refixes.component.TickThrottled;
import cc.irori.refixes.config.impl.AiTickThrottlerConfig;
import cc.irori.refixes.early.accessor.MotionControllerAccess;
import cc.irori.refixes.early.util.RefixesScheduler;
import cc.irori.refixes.early.util.ThrottleOwnership;
import cc.irori.refixes.jfr.RefixesEvents;
import cc.irori.refixes.util.Logs;
import cc.irori.refixes.version.HytaleVersion;
import com.hypixel.hytale.builtin.mounts.NPCMountComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.data.unknown.UnknownComponents;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.Frozen;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.damage.DamageDataComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.components.StepComponent;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerFly;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerWalk;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.role.support.CombatSupport;
import com.hypixel.hytale.server.npc.role.support.MarkedEntitySupport;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Distance-based LOD for AI entity ticking
 *
 * Freezes distant NPCs and reduces their tick rate based on chunk proximity to the nearest player
 * ≤ 2 chunks (~64 blocks): full AI tick rate
 * ≤ 4 chunks (~128 blocks): mid tick rate (0.2s)
 * ≤ 6 chunks (~192 blocks): far tick rate (0.5s)
 * > 6 chunks: very far tick rate (1.0s)
 */
public class AiTickThrottlerService {

    private static final HytaleLogger LOGGER = Logs.logger();
    private static final LongAdder FROZE = RefixesEvents.counter("AiTickThrottler froze");
    private static final LongAdder UNFROZE = RefixesEvents.counter("AiTickThrottler unfroze");
    private static final LongAdder BUDGET_EXCEEDED = RefixesEvents.counter("AiTickThrottler budget exceeded");

    // Cached component types, resolved once via resolveComponentTypes()
    private ComponentType<EntityStore, ?> npcType;
    private ComponentType<EntityStore, TransformComponent> transformType;
    private ComponentType<EntityStore, UUIDComponent> uuidType;
    private ComponentType<EntityStore, Frozen> frozenType;
    private ComponentType<EntityStore, StepComponent> stepType;
    private ComponentType<EntityStore, TickThrottled> tickThrottledType;
    private ComponentType<EntityStore, Player> playerType;
    private ComponentType<EntityStore, ? extends INonPlayerCharacter> npcEntityType;
    private ComponentType<EntityStore, NPCMountComponent> mountType;
    private ComponentType<EntityStore, MovementStatesComponent> movementStatesType;
    private ComponentType<EntityStore, DeathComponent> deathType;
    private ComponentType<EntityStore, CombatSupport> combatType;
    private ComponentType<EntityStore, MarkedEntitySupport> markedType;
    private ComponentType<EntityStore, DamageDataComponent> damageDataType;
    private ComponentType<EntityStore, StateSupport> stateSupportType;
    private Query<EntityStore> npcQuery;
    private final AtomicBoolean componentTypesFailureLogged = new AtomicBoolean();

    private final Map<World, WorldState> worldStates = new ConcurrentHashMap<>();

    private volatile String[] excludedTypesArray;
    private volatile Set<String> excludedTypesSet;

    private ScheduledFuture<?> task;
    private volatile double lastCycleMs;
    private AutoCloseable throttledGauge;
    private AutoCloseable activeGauge;
    private AutoCloseable cycleMsGauge;
    private volatile boolean stopped;
    private volatile long pollNanos;

    public void registerService() {
        int intervalMs = Math.max(20, AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.UPDATE_INTERVAL_MS));
        pollNanos = TimeUnit.MILLISECONDS.toNanos(intervalMs);
        task = RefixesScheduler.get()
                .scheduleAtFixedRate(
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
        throttledGauge = RefixesEvents.gauge("AiTickThrottler throttled", () -> getThrottledCount());
        activeGauge = RefixesEvents.gauge("AiTickThrottler active", () -> getActiveNpcCount());
        cycleMsGauge = RefixesEvents.gauge("AiTickThrottler cycle ms", () -> lastCycleMs);
    }

    public void unregisterService() {
        stopped = true;
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        closeQuietly(throttledGauge);
        closeQuietly(activeGauge);
        closeQuietly(cycleMsGauge);
        throttledGauge = null;
        activeGauge = null;
        cycleMsGauge = null;
        // Unfreeze all entities we froze before clearing state
        unfreezeAllWorld();
        worldStates.clear();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void unfreezeAllWorld() {
        for (World world : Universe.get().getWorlds().values()) {
            WorldState state = worldStates.get(world);
            if (state != null) {
                try {
                    world.execute(() -> unfreezeAll(world, state));
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private void unfreezeAll(World world, WorldState state) {
        if (!resolveComponentTypes()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store.isShutdown()) {
            return;
        }
        ComponentType<EntityStore, ?> marker =
                store.getRegistry().getData().getComponentType(ThrottleOwnership.MARKER_ID);
        ComponentType<EntityStore, UnknownComponents<EntityStore>> unknownType =
                store.getRegistry().getUnknownComponentType();
        store.forEachEntityParallel(npcQuery, (index, archetypeChunk, commandBuffer) -> {
            if (marker != null) {
                if (!archetypeChunk.getArchetype().contains(marker)) {
                    return;
                }
            } else {
                UnknownComponents<EntityStore> unknown = archetypeChunk.getComponent(index, unknownType);
                if (unknown == null || unknown.getUnknownComponents().remove(ThrottleOwnership.MARKER_ID) == null) {
                    return;
                }
            }

            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            commandBuffer.tryRemoveComponent(ref, frozenType);
            commandBuffer.tryRemoveComponent(ref, stepType);
            if (marker != null) {
                commandBuffer.tryRemoveComponent(ref, marker);
            }
        });
    }

    private Set<String> resolveExcludedTypes(AiTickThrottlerConfig cfg) {
        String[] arr = cfg.getValue(AiTickThrottlerConfig.THROTTLE_EXCLUDED_NPC_TYPES);
        Set<String> cached = excludedTypesSet;
        if (arr == excludedTypesArray && cached != null) {
            return cached;
        }
        Set<String> built = arr.length == 0 ? Set.of() : Set.copyOf(Arrays.asList(arr));
        excludedTypesArray = arr;
        excludedTypesSet = built;
        return built;
    }

    private void throttle() {
        AiTickThrottlerConfig cfg = AiTickThrottlerConfig.get();

        Universe universe = Universe.get();
        Map<String, World> worlds = universe.getWorlds();
        worldStates.keySet().removeIf(world -> universe.getWorld(world.getName()) != world);

        for (World world : worlds.values()) {
            WorldState state = worldStates.computeIfAbsent(world, _k -> new WorldState());
            // Skip if prior cycle's world.execute is still queued (pile-up guard).
            if (!state.inProgress.compareAndSet(false, true)) {
                continue;
            }
            try {
                world.execute(() -> {
                    try {
                        processWorld(world, cfg);
                    } finally {
                        state.inProgress.set(false);
                    }
                });
            } catch (RuntimeException e) {
                state.inProgress.set(false);
            }
        }
    }

    public int getThrottledCount() {
        int total = 0;
        for (WorldState state : worldStates.values()) {
            for (AiLodEntry entry : state.entries.values()) {
                if (entry.throttled) {
                    total++;
                }
            }
        }
        return total;
    }

    public int getActiveNpcCount() {
        int total = 0;
        for (WorldState state : worldStates.values()) {
            total += state.lastScanned;
        }
        return total;
    }

    public void advanceClock(Store<EntityStore> store, float dt) {
        if (stopped || !resolveComponentTypes()) {
            return;
        }
        World world = store.getExternalData().getWorld();
        WorldState state = worldStates.computeIfAbsent(world, _k -> new WorldState());
        if (world.getWorldConfig().isAllNPCFrozen()) {
            deferPendingSteps(store);
        } else if (!world.isPaused()) {
            long tickNanos = (long) (dt * 1_000_000_000.0);
            state.clockNanos += tickNanos;
            if (state.releasePending) {
                completeReleases(store, state, tickNanos);
            }
        }
    }

    public void releaseForcePushed(
            float dt,
            int index,
            ArchetypeChunk<EntityStore> chunk,
            Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer) {
        if (stopped || !resolveComponentTypes()) {
            return;
        }
        INonPlayerCharacter npcEntity = chunk.getComponent(index, npcEntityType);
        Role role = npcEntity == null ? null : HytaleVersion.get().role(npcEntity);
        if (role == null) {
            return;
        }
        var controller = role.getActiveMotionController();
        if (controller == null) {
            return;
        }
        boolean forcePushed = controller.isForcePushed();
        StepComponent step = chunk.getComponent(index, stepType);
        if (!forcePushed && (step == null || !(controller instanceof MotionControllerWalk))) {
            return;
        }
        World world = store.getExternalData().getWorld();
        if (world.getWorldConfig().isAllNPCFrozen()
                || !AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.THROTTLE_EXCLUDE_AIRBORNE_OR_DEAD)) {
            return;
        }
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (!forcePushed) {
            ((MotionControllerAccess) controller).refixes$readEntityPosition(ref, store);
            if (controller.onGround()) {
                return;
            }
        }
        TickThrottled marker = chunk.getComponent(index, tickThrottledType);
        StepComponent pending = marker == null ? null : marker.getPendingStep();
        float owed = (step == null ? 0f : step.getTickLength()) + (pending == null ? 0f : pending.getTickLength());
        commandBuffer.tryRemoveComponent(ref, frozenType);
        commandBuffer.tryRemoveComponent(ref, stepType);
        commandBuffer.tryRemoveComponent(ref, tickThrottledType);
        WorldState state = worldStates.get(world);
        UUIDComponent uuid = chunk.getComponent(index, uuidType);
        AiLodEntry entry = state == null || uuid == null ? null : state.entries.get(uuid.getUuid());
        if (entry != null) {
            if (entry.pausedSinceNanos == 0L) {
                entry.nextTickNanos -= (long) (owed * 1_000_000_000.0);
            }
            markInactive(entry, state.clockNanos - (long) (dt * 1_000_000_000.0));
        }
    }

    private void deferPendingSteps(Store<EntityStore> store) {
        store.forEachChunk(Query.and(stepType, tickThrottledType), (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                commandBuffer.putComponent(
                        ref, tickThrottledType, TickThrottled.withPendingStep(chunk.getComponent(i, stepType)));
                commandBuffer.tryRemoveComponent(ref, stepType);
            }
        });
    }

    private void completeReleases(Store<EntityStore> store, WorldState state, long tickNanos) {
        state.releasePending = false;
        float maxStep = AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.MAX_STEP_SECONDS);
        store.forEachChunk(Query.and(frozenType, tickThrottledType), (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                TickThrottled marker = chunk.getComponent(i, tickThrottledType);
                if (!marker.isReleasing()) {
                    continue;
                }
                if (chunk.getComponent(i, stepType) != null) {
                    state.releasePending = true;
                    continue;
                }
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                UUIDComponent uuid = chunk.getComponent(i, uuidType);
                AiLodEntry entry = uuid == null ? null : state.entries.get(uuid.getUuid());
                if (entry != null && entry.pausedSinceNanos == 0L) {
                    long behind = state.clockNanos - entry.nextTickNanos;
                    if (behind - tickNanos > entry.intervalNanos) {
                        commandBuffer.putComponent(
                                ref, stepType, new StepComponent(stepLength(0f, behind / 1_000_000_000f, maxStep, 1f)));
                        entry.nextTickNanos = state.clockNanos;
                        state.releasePending = true;
                        continue;
                    }
                }
                commandBuffer.tryRemoveComponent(ref, frozenType);
                commandBuffer.tryRemoveComponent(ref, tickThrottledType);
                markInactive(entry, state.clockNanos - tickNanos);
            }
        });
    }

    private void processWorld(World world, AiTickThrottlerConfig cfg) {
        if (stopped) {
            return;
        }

        WorldState state = worldStates.computeIfAbsent(world, _k -> new WorldState());
        if (world.isPaused() || world.getWorldConfig().isAllNPCFrozen()) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        if (store.isShutdown()) {
            return;
        }

        // Precompute player chunk positions
        List<int[]> playerChunks = collectPlayerChunkPositions(world.getPlayerRefs());
        boolean freezeWithoutPlayers =
                playerChunks.isEmpty() && !cfg.getValue(AiTickThrottlerConfig.STEP_WITHOUT_PLAYERS);

        long now = state.clockNanos;

        int nearChunks = Math.max(0, cfg.getValue(AiTickThrottlerConfig.NEAR_CHUNKS));
        int midChunks = Math.max(nearChunks, cfg.getValue(AiTickThrottlerConfig.MID_CHUNKS));
        int farChunks = Math.max(midChunks, cfg.getValue(AiTickThrottlerConfig.FAR_CHUNKS));

        int hysteresis = Math.max(0, cfg.getValue(AiTickThrottlerConfig.ACTIVATION_HYSTERESIS_CHUNKS));
        int maxUnfreezes = Math.max(1, cfg.getValue(AiTickThrottlerConfig.MAX_UNFREEZES_PER_TICK));
        int maxFreezes = Math.max(1, cfg.getValue(AiTickThrottlerConfig.MAX_FREEZES_PER_TICK));
        AtomicInteger unfreezeCount = new AtomicInteger(0);
        AtomicInteger freezeCount = new AtomicInteger(0);

        float rawMid = cfg.getValue(AiTickThrottlerConfig.MID_TICK_SECONDS);
        float rawFar = cfg.getValue(AiTickThrottlerConfig.FAR_TICK_SECONDS);
        float rawVeryFar = cfg.getValue(AiTickThrottlerConfig.VERY_FAR_TICK_SECONDS);
        float pollSeconds = Math.min(pollNanos / 1_000_000_000f, Math.max(rawMid, Math.max(rawFar, rawVeryFar)));
        float minTick = Math.max(cfg.getValue(AiTickThrottlerConfig.MIN_TICK_SECONDS), pollSeconds);
        float maxStep = cfg.getValue(AiTickThrottlerConfig.MAX_STEP_SECONDS);
        float dilation = store.getResource(TimeResource.getResourceType()).getTimeDilationModifier();
        StepComponent midStep =
                freezeWithoutPlayers ? null : new StepComponent(stepLength(minTick, rawMid, maxStep, dilation));
        StepComponent farStep =
                freezeWithoutPlayers ? null : new StepComponent(stepLength(minTick, rawFar, maxStep, dilation));
        StepComponent veryFarStep =
                freezeWithoutPlayers ? null : new StepComponent(stepLength(minTick, rawVeryFar, maxStep, dilation));
        long earlyNanos = (long) (pollNanos * dilation / 2);

        Set<String> excludedNpcTypes = resolveExcludedTypes(cfg);
        boolean excludeMounts = cfg.getValue(AiTickThrottlerConfig.THROTTLE_EXCLUDE_MOUNTS);
        boolean excludeFlying = cfg.getValue(AiTickThrottlerConfig.THROTTLE_EXCLUDE_FLYING);
        boolean excludeAirborneOrDead = cfg.getValue(AiTickThrottlerConfig.THROTTLE_EXCLUDE_AIRBORNE_OR_DEAD);
        java.time.Duration outOfCombatDelay =
                world.getGameplayConfig().getCombatConfig().getOutOfCombatDelay();
        Instant combatCutoff =
                store.getResource(TimeResource.getResourceType()).getNow().minus(outOfCombatDelay);
        long transitionWindowNanos = outOfCombatDelay.toNanos();

        // Reuse seen set to avoid allocating a new ConcurrentHashMap each cycle
        state.seen.clear();

        int maxCycleMs = Math.max(0, cfg.getValue(AiTickThrottlerConfig.MAX_CYCLE_MS));
        long budgetNanos = maxCycleMs == 0 ? Long.MAX_VALUE : TimeUnit.MILLISECONDS.toNanos(maxCycleMs);
        long cycleStartNanos = System.nanoTime();

        int shards = Math.max(1, cfg.getValue(AiTickThrottlerConfig.SCAN_SHARDS));
        int shardIndex = Math.floorMod(state.shardCursor++, shards);

        store.forEachEntityParallel(npcQuery, (index, archetypeChunk, commandBuffer) -> {
            UUIDComponent uuid = archetypeChunk.getComponent(index, uuidType);
            UUID entityId = uuid == null ? null : uuid.getUuid();
            if (entityId != null) {
                state.seen.add(entityId);
            }

            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            boolean frozen = archetypeChunk.getComponent(index, frozenType) != null;
            boolean throttled = archetypeChunk.getComponent(index, tickThrottledType) != null;
            if (frozen && !throttled) {
                if (entityId != null) {
                    state.entries.remove(entityId);
                }
                return;
            }
            if (throttled && !frozen) {
                commandBuffer.tryRemoveComponent(ref, stepType);
                commandBuffer.tryRemoveComponent(ref, tickThrottledType);
                if (entityId != null) {
                    markInactive(state.entries.get(entityId), now);
                }
                return;
            }

            if (isExcluded(
                    index,
                    archetypeChunk,
                    commandBuffer,
                    excludedNpcTypes,
                    excludeMounts,
                    excludeFlying,
                    excludeAirborneOrDead,
                    combatCutoff)) {
                if (throttled) {
                    if (frozen
                            && deferRelease(
                                    index,
                                    archetypeChunk,
                                    commandBuffer,
                                    state,
                                    entityId == null ? null : state.entries.get(entityId),
                                    maxStep)) {
                        return;
                    }
                    commandBuffer.tryRemoveComponent(ref, frozenType);
                    commandBuffer.tryRemoveComponent(ref, stepType);
                    commandBuffer.tryRemoveComponent(ref, tickThrottledType);
                    if (entityId != null) {
                        markInactive(state.entries.get(entityId), now);
                    }
                }
                return;
            }

            if (freezeWithoutPlayers) {
                if (entityId != null) {
                    AiLodEntry entry = state.entries.get(entityId);
                    if (entry != null) {
                        pause(entry, now);
                        entry.throttled = true;
                    }
                }
                if (!frozen) {
                    commandBuffer.ensureComponent(ref, frozenType);
                }
                if (!throttled) {
                    commandBuffer.putComponent(ref, tickThrottledType, TickThrottled.get());
                }
                if (archetypeChunk.getComponent(index, stepType) != null) {
                    commandBuffer.tryRemoveComponent(ref, stepType);
                }
                TickThrottled idleMarker = archetypeChunk.getComponent(index, tickThrottledType);
                if (idleMarker != null && idleMarker.isReleasing()) {
                    commandBuffer.putComponent(ref, tickThrottledType, TickThrottled.get());
                }
                return;
            }

            if (entityId == null) {
                return;
            }

            TransformComponent transform = archetypeChunk.getComponent(index, transformType);
            if (transform == null) {
                return;
            }

            int entityChunkX = ChunkUtil.chunkCoordinate(transform.getPosition().x());
            int entityChunkZ = ChunkUtil.chunkCoordinate(transform.getPosition().z());
            int chunkDist = closestPlayerChunkDistance(entityChunkX, entityChunkZ, playerChunks);
            boolean transitioning = noteStateTransition(
                    archetypeChunk.getComponent(index, stateSupportType),
                    state.entries.get(entityId),
                    now,
                    transitionWindowNanos);

            // Already throttled → unfreeze at nearChunks (tight)
            // Not throttled → freeze at nearChunks + hysteresis (wider)
            double intervalSec;
            if (throttled) {
                intervalSec = computeInterval(chunkDist, nearChunks, midChunks, farChunks, rawMid, rawFar, rawVeryFar);
            } else {
                intervalSec = computeInterval(
                        chunkDist,
                        nearChunks + hysteresis,
                        midChunks + hysteresis,
                        farChunks + hysteresis,
                        rawMid,
                        rawFar,
                        rawVeryFar);
            }

            if (transitioning) {
                intervalSec = 0.0;
            }

            // If near enough, remove throttling
            if (intervalSec <= 0.0) {
                if (!throttled) {
                    return;
                }
                if (!frozen || unfreezeCount.incrementAndGet() <= maxUnfreezes) {
                    if (frozen
                            && deferRelease(
                                    index,
                                    archetypeChunk,
                                    commandBuffer,
                                    state,
                                    state.entries.get(entityId),
                                    maxStep)) {
                        return;
                    }
                    commandBuffer.tryRemoveComponent(ref, frozenType);
                    commandBuffer.tryRemoveComponent(ref, stepType);
                    commandBuffer.tryRemoveComponent(ref, tickThrottledType);
                    markInactive(state.entries.get(entityId), now);
                    return;
                }
                intervalSec = rawMid;
            }

            if (!throttled) {
                if (Math.floorMod(entityId.hashCode(), shards) != shardIndex
                        || System.nanoTime() - cycleStartNanos > budgetNanos) {
                    return;
                }
                if (freezeCount.incrementAndGet() > maxFreezes) {
                    return;
                }
                commandBuffer.ensureComponent(ref, frozenType);
                commandBuffer.putComponent(ref, tickThrottledType, TickThrottled.get());
            }
            AiLodEntry entry = state.entries.computeIfAbsent(entityId, _k -> new AiLodEntry());

            long intervalNanos = (long) (Math.max(minTick, intervalSec) * dilation * 1_000_000_000.0);
            StepComponent pendingStep = archetypeChunk.getComponent(index, stepType);
            TickThrottled marker = archetypeChunk.getComponent(index, tickThrottledType);
            StepComponent deferredStep = marker == null ? null : marker.getPendingStep();
            long stepNanos = isDue(entry, intervalNanos, now, earlyNanos, pendingStep != null || deferredStep != null);
            if (deferredStep != null && pendingStep == null) {
                commandBuffer.putComponent(ref, stepType, deferredStep);
                commandBuffer.putComponent(ref, tickThrottledType, TickThrottled.get());
                return;
            }
            if (marker != null && marker.isReleasing()) {
                commandBuffer.putComponent(ref, tickThrottledType, TickThrottled.get());
            }
            if (stepNanos >= 0L) {
                // Use pre-computed step for the matching tier
                StepComponent step;
                if (stepNanos > intervalNanos) {
                    step = new StepComponent(stepLength(0f, stepNanos / 1_000_000_000f, maxStep, 1f));
                } else if (intervalSec == rawMid) {
                    step = midStep;
                } else if (intervalSec == rawFar) {
                    step = farStep;
                } else {
                    step = veryFarStep;
                }
                commandBuffer.putComponent(ref, stepType, step);
            }
        });

        state.lastScanned = state.seen.size();
        state.entries.keySet().removeIf(id -> !state.seen.contains(id));

        int froze = Math.min(freezeCount.get(), maxFreezes);
        if (froze > 0) {
            FROZE.add(froze);
        }
        int unfroze = Math.min(unfreezeCount.get(), maxUnfreezes);
        if (unfroze > 0) {
            UNFROZE.add(unfroze);
        }
        long cycleNanos = System.nanoTime() - cycleStartNanos;
        lastCycleMs = cycleNanos / 1_000_000.0;
        if (cycleNanos > budgetNanos) {
            BUDGET_EXCEEDED.increment();
        }
    }

    private boolean deferRelease(
            int index,
            ArchetypeChunk<EntityStore> chunk,
            CommandBuffer<EntityStore> commandBuffer,
            WorldState state,
            AiLodEntry entry,
            float maxStep) {
        long now = state.clockNanos;
        TickThrottled marker = chunk.getComponent(index, tickThrottledType);
        StepComponent pending = marker == null ? null : marker.getPendingStep();
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (chunk.getComponent(index, stepType) != null) {
            if (marker != null && pending == null) {
                commandBuffer.putComponent(ref, tickThrottledType, TickThrottled.releasing());
                state.releasePending = true;
            }
            return true;
        }
        if (pending != null) {
            commandBuffer.putComponent(ref, stepType, pending);
            commandBuffer.putComponent(ref, tickThrottledType, TickThrottled.releasing());
            state.releasePending = true;
            return true;
        }
        if (marker != null && marker.isReleasing()) {
            state.releasePending = true;
            return true;
        }
        if (entry == null || entry.intervalNanos == 0L || entry.pausedSinceNanos != 0L) {
            return false;
        }
        long owed = now - entry.nextTickNanos;
        if (owed <= entry.intervalNanos) {
            return false;
        }
        commandBuffer.putComponent(
                ref, stepType, new StepComponent(stepLength(0f, owed / 1_000_000_000f, maxStep, 1f)));
        commandBuffer.putComponent(ref, tickThrottledType, TickThrottled.releasing());
        state.releasePending = true;
        entry.nextTickNanos = now;
        return true;
    }

    private boolean isExcluded(
            int index,
            ArchetypeChunk<EntityStore> archetypeChunk,
            CommandBuffer<EntityStore> commandBuffer,
            Set<String> excludedNpcTypes,
            boolean excludeMounts,
            boolean excludeFlying,
            boolean excludeAirborneOrDead,
            Instant combatCutoff) {
        if (playerType != null && archetypeChunk.getArchetype().contains(playerType)) {
            return true;
        }
        if (excludeAirborneOrDead && archetypeChunk.getComponent(index, deathType) != null) {
            return true;
        }
        INonPlayerCharacter npcEntity = archetypeChunk.getComponent(index, npcEntityType);
        Role role = npcEntity == null ? null : HytaleVersion.get().role(npcEntity);
        if ((excludeFlying || excludeAirborneOrDead) && role != null) {
            var controller = role.getActiveMotionController();
            if ((excludeFlying && controller instanceof MotionControllerFly)
                    || (excludeAirborneOrDead
                            && controller != null
                            && (!controller.onGround() || controller.isForcePushed()))) {
                return true;
            }
        }
        if (excludeMounts && mountType != null && archetypeChunk.getComponent(index, mountType) != null) {
            return true;
        }
        if ((excludeFlying || excludeAirborneOrDead) && movementStatesType != null) {
            MovementStatesComponent ms = archetypeChunk.getComponent(index, movementStatesType);
            if (ms != null) {
                var states = ms.getMovementStates();
                if (states != null && ((excludeAirborneOrDead && states.falling) || (excludeFlying && states.flying))) {
                    return true;
                }
            }
        }
        if (!excludedNpcTypes.isEmpty()) {
            if (npcEntity != null && excludedNpcTypes.contains(npcEntity.getNPCTypeId())) {
                return true;
            }
        }
        if (combatType != null) {
            CombatSupport combat = archetypeChunk.getComponent(index, combatType);
            if (combat != null && combat.isExecutingAttack()) {
                return true;
            }
        }
        if (damageDataType != null) {
            DamageDataComponent damageData = archetypeChunk.getComponent(index, damageDataType);
            if (damageData != null && damageData.getLastCombatAction().isAfter(combatCutoff)) {
                return true;
            }
        }
        if (markedType != null && playerType != null) {
            MarkedEntitySupport marked = archetypeChunk.getComponent(index, markedType);
            if (marked != null) {
                for (int i = 0; i < marked.getMarkedEntitySlotCount(); i++) {
                    Ref<EntityStore> target = marked.getMarkedEntityRef(i);
                    if (target != null
                            && target.getStore() == commandBuffer.getStore()
                            && commandBuffer.getArchetype(target).contains(playerType)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean noteStateTransition(StateSupport stateSupport, AiLodEntry entry, long now, long window) {
        if (stateSupport == null || entry == null) {
            return false;
        }
        long observed = ((long) stateSupport.getStateIndex() << 32) | (stateSupport.getSubStateIndex() & 0xffffffffL);
        if (entry.stateObserved && entry.lastState != observed) {
            entry.transitionUntilNanos = now + window;
        }
        entry.lastState = observed;
        entry.stateObserved = true;
        return now < entry.transitionUntilNanos;
    }

    private static void markInactive(AiLodEntry entry, long now) {
        if (entry != null) {
            pause(entry, now);
            entry.throttled = false;
        }
    }

    private static void pause(AiLodEntry entry, long now) {
        if (entry.pausedSinceNanos == 0L) {
            entry.pausedSinceNanos = now;
        }
    }

    private static long isDue(AiLodEntry entry, long intervalNanos, long now, long earlyNanos, boolean pending) {
        entry.throttled = true;
        if (entry.pausedSinceNanos != 0L) {
            entry.nextTickNanos += now - entry.pausedSinceNanos;
            entry.pausedSinceNanos = 0L;
        }
        if (entry.intervalNanos == 0L) {
            entry.nextTickNanos = pending ? now + intervalNanos : now;
        }
        entry.intervalNanos = intervalNanos;
        if (pending || now + earlyNanos < entry.nextTickNanos) {
            return -1L;
        }
        long stepNanos = Math.max(intervalNanos, now - entry.nextTickNanos);
        entry.nextTickNanos = Math.max(entry.nextTickNanos + intervalNanos, now);
        return stepNanos;
    }

    private static float stepLength(float minTick, float interval, float maxStep, float dilation) {
        float length = Math.max(minTick, interval) * dilation;
        return maxStep > 0f ? Math.min(length, maxStep) : length;
    }

    private static double computeInterval(
            int chunkDist, int nearChunks, int midChunks, int farChunks, float rawMid, float rawFar, float rawVeryFar) {
        if (chunkDist <= nearChunks) {
            return 0.0;
        }
        if (chunkDist <= midChunks) {
            return rawMid;
        }
        if (chunkDist <= farChunks) {
            return rawFar;
        }
        return rawVeryFar;
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
            int chunkX = ChunkUtil.chunkCoordinate(transform.getPosition().x());
            int chunkZ = ChunkUtil.chunkCoordinate(transform.getPosition().z());
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
                && tickThrottledType != null
                && npcEntityType != null
                && deathType != null) {
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
            if (npcEntityType == null) npcEntityType = HytaleVersion.get().npcComponentType();
            if (mountType == null) mountType = NPCMountComponent.getComponentType();
            if (movementStatesType == null) movementStatesType = MovementStatesComponent.getComponentType();
            if (deathType == null) deathType = DeathComponent.getComponentType();
            if (combatType == null) combatType = CombatSupport.getComponentType();
            if (markedType == null) markedType = MarkedEntitySupport.getComponentType();
            if (damageDataType == null) damageDataType = DamageDataComponent.getComponentType();
            if (stateSupportType == null) stateSupportType = StateSupport.getComponentType();

            if (npcQuery == null) {
                npcQuery = Query.and(npcType, transformType);
            }
        } catch (Throwable t) {
            if (componentTypesFailureLogged.compareAndSet(false, true)) {
                LOGGER.atWarning().withCause(t).log(
                        "Could not resolve NPC component types, so AI tick throttling is skipped until they resolve");
            }
            return false;
        }
        return npcType != null
                && transformType != null
                && uuidType != null
                && frozenType != null
                && stepType != null
                && tickThrottledType != null
                && npcEntityType != null
                && deathType != null;
    }

    private static final class WorldState {
        final java.util.concurrent.atomic.AtomicBoolean inProgress =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        final Map<UUID, AiLodEntry> entries = new ConcurrentHashMap<>();
        final Set<UUID> seen = ConcurrentHashMap.newKeySet();
        volatile int lastScanned;
        int shardCursor;
        long clockNanos = 1L;
        volatile boolean releasePending;
    }

    private static final class AiLodEntry {
        volatile boolean throttled = true;
        long pausedSinceNanos;
        long intervalNanos;
        long nextTickNanos;
        long lastState;
        boolean stateObserved;
        long transitionUntilNanos;
    }
}
