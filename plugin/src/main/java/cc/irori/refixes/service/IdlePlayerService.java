package cc.irori.refixes.service;

import cc.irori.refixes.config.impl.IdlePlayerHandlerConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Detects AFK players and reduces their view/hot/minLoaded
 * radius to save chunk loading resources. Restores settings when they move.
 */
public class IdlePlayerService {

    private static final HytaleLogger LOGGER = Logs.logger();

    private final Map<UUID, PlayerIdleState> playerStates = new ConcurrentHashMap<>();
    private ScheduledFuture<?> task;

    public void registerService() {
        int intervalSec =
                Math.max(5, IdlePlayerHandlerConfig.get().getValue(IdlePlayerHandlerConfig.CHECK_INTERVAL_SECONDS));
        task = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                    try {
                        evaluatePlayers();
                    } catch (Exception e) {
                        LOGGER.atSevere().withCause(e).log("Error in idle player handler");
                    }
                },
                5000,
                intervalSec * 1000L,
                TimeUnit.MILLISECONDS);
    }

    public void unregisterService() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        playerStates.clear();
    }

    private void evaluatePlayers() {
        IdlePlayerHandlerConfig cfg = IdlePlayerHandlerConfig.get();
        if (!cfg.getValue(IdlePlayerHandlerConfig.ENABLED)) {
            return;
        }

        long now = System.currentTimeMillis();
        long timeoutMs = Math.max(30, cfg.getValue(IdlePlayerHandlerConfig.IDLE_TIMEOUT_SECONDS)) * 1000L;

        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            UUID uuid = playerRef.getUuid();
            PlayerIdleState state = playerStates.computeIfAbsent(uuid, _u -> new PlayerIdleState());

            Vector3d currentPos = playerRef.getTransform().getPosition();

            // Detect movement
            if (state.lastPosition != null
                    && hasPlayerMoved(
                            state.lastPosition, currentPos, cfg.getValue(IdlePlayerHandlerConfig.MOVEMENT_THRESHOLD))) {
                state.markActivity();
                if (state.wasIdle) {
                    restorePlayerSettings(playerRef, state, cfg);
                }
            }
            state.lastPosition = new Vector3d(currentPos);

            if (!state.wasIdle && now - state.lastActivityMs > timeoutMs) {
                applyIdleSettings(playerRef, state, cfg);
            }
        }

        // Clean up disconnected players
        playerStates.keySet().removeIf(uuid -> {
            for (PlayerRef p : Universe.get().getPlayers()) {
                if (p != null && p.getUuid().equals(uuid)) {
                    return false;
                }
            }
            return true;
        });
    }

    private void applyIdleSettings(PlayerRef playerRef, PlayerIdleState state, IdlePlayerHandlerConfig cfg) {
        if (state.pendingApply) {
            return;
        }
        state.pendingApply = true;

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            state.pendingApply = false;
            return;
        }

        Store<EntityStore> store = entityRef.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        if (world == null) {
            state.pendingApply = false;
            return;
        }

        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> world.execute(() -> {
                    try {
                        ChunkTracker tracker = playerRef.getChunkTracker();

                        if (cfg.getValue(IdlePlayerHandlerConfig.REDUCE_VIEW_RADIUS)) {
                            Player player = getPlayerComponent(playerRef);
                            if (player != null) {
                                int currentView = player.getClientViewRadius();
                                int idleView = Math.max(2, cfg.getValue(IdlePlayerHandlerConfig.IDLE_VIEW_RADIUS));
                                if (currentView > idleView) {
                                    state.savedViewRadius = currentView;
                                    player.setClientViewRadius(idleView);
                                }
                            }
                        }

                        if (cfg.getValue(IdlePlayerHandlerConfig.REDUCE_HOT_RADIUS)) {
                            int currentHot = tracker.getMaxHotLoadedChunksRadius();
                            int idleHot = Math.max(2, cfg.getValue(IdlePlayerHandlerConfig.IDLE_HOT_RADIUS));
                            if (currentHot > idleHot) {
                                state.savedHotRadius = currentHot;
                                tracker.setMaxHotLoadedChunksRadius(idleHot);
                            }
                        }

                        if (cfg.getValue(IdlePlayerHandlerConfig.REDUCE_MIN_LOADED_RADIUS)) {
                            int currentMinLoaded = tracker.getMinLoadedChunksRadius();
                            int idleMinLoaded = Math.max(2, cfg.getValue(IdlePlayerHandlerConfig.IDLE_MIN_LOADED_RADIUS));
                            if (currentMinLoaded > idleMinLoaded) {
                                state.savedMinLoadedRadius = currentMinLoaded;
                                tracker.setMinLoadedChunksRadius(idleMinLoaded);
                            }
                        }

                        state.wasIdle = true;
                        LOGGER.atInfo().log("Applied idle settings for player %s", playerRef.getUuid());
                    } catch (Throwable t) {
                        LOGGER.atWarning().withCause(t).log(
                                "Failed to apply idle settings for player %s", playerRef.getUuid());
                    } finally {
                        state.pendingApply = false;
                    }
                }),
                50,
                TimeUnit.MILLISECONDS);
    }

    private void restorePlayerSettings(PlayerRef playerRef, PlayerIdleState state, IdlePlayerHandlerConfig cfg) {
        if (state.pendingRestore) {
            return;
        }
        state.pendingRestore = true;

        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            state.pendingRestore = false;
            return;
        }

        Store<EntityStore> store = entityRef.getStore();
        World world = ((EntityStore) store.getExternalData()).getWorld();
        if (world == null) {
            state.pendingRestore = false;
            return;
        }

        HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> world.execute(() -> {
                    try {
                        ChunkTracker tracker = playerRef.getChunkTracker();

                        if (state.savedViewRadius != null) {
                            Player player = getPlayerComponent(playerRef);
                            if (player != null) {
                                player.setClientViewRadius(state.savedViewRadius);
                            }
                            state.savedViewRadius = null;
                        }

                        if (state.savedHotRadius != null) {
                            tracker.setMaxHotLoadedChunksRadius(state.savedHotRadius);
                            state.savedHotRadius = null;
                        }

                        if (state.savedMinLoadedRadius != null) {
                            tracker.setMinLoadedChunksRadius(state.savedMinLoadedRadius);
                            state.savedMinLoadedRadius = null;
                        }

                        state.wasIdle = false;
                        LOGGER.atInfo().log("Restored settings for player %s", playerRef.getUuid());
                    } catch (Throwable t) {
                        LOGGER.atWarning().withCause(t).log(
                                "Failed to restore settings for player %s", playerRef.getUuid());
                    } finally {
                        state.pendingRestore = false;
                    }
                }),
                50,
                TimeUnit.MILLISECONDS);
    }

    private static Player getPlayerComponent(PlayerRef playerRef) {
        try {
            Ref<EntityStore> entityRef = playerRef.getReference();
            if (entityRef == null || !entityRef.isValid()) {
                return null;
            }
            return entityRef.getStore().getComponent(entityRef, Player.getComponentType());
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean hasPlayerMoved(Vector3d prev, Vector3d curr, double threshold) {
        double dx = prev.getX() - curr.getX();
        double dz = prev.getZ() - curr.getZ();
        // Only check XZ movement; ignore Y to avoid false positives from falling
        return dx * dx + dz * dz > threshold * threshold;
    }

    private static final class PlayerIdleState {
        volatile long lastActivityMs = System.currentTimeMillis();
        volatile Vector3d lastPosition;
        volatile boolean wasIdle;
        volatile Integer savedViewRadius;
        volatile Integer savedHotRadius;
        volatile Integer savedMinLoadedRadius;
        volatile boolean pendingApply;
        volatile boolean pendingRestore;

        void markActivity() {
            this.lastActivityMs = System.currentTimeMillis();
        }
    }
}
