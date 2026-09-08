package cc.irori.refixes.service;

import cc.irori.refixes.compat.BlackboxBridge;
import cc.irori.refixes.config.impl.IdlePlayerHandlerConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.ChunkTracker;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.joml.Vector3d;

/**
 * Detects AFK players and reduces their view/hot/minLoaded
 * radius to save chunk loading resources. Restores settings when they move.
 */
public class IdlePlayerService {

    private static final HytaleLogger LOGGER = Logs.logger();

    private final Map<UUID, PlayerIdleState> playerStates = new ConcurrentHashMap<>();
    private ScheduledFuture<?> task;
    private boolean registered;
    private AutoCloseable idleGauge;

    public synchronized void registerService() {
        if (registered) {
            return;
        }
        registered = true;
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
        idleGauge = BlackboxBridge.registerGauge("IdlePlayer idle", () -> getIdleCount());
        if (IdlePlayerHandlerConfig.get().getValue(IdlePlayerHandlerConfig.REDUCE_MIN_LOADED_RADIUS)) {
            LOGGER.atInfo().log(
                    "AFK minimum-loaded radius affects legacy columns only; independent sections use view/hot radii");
        }
    }

    public synchronized void unregisterService() {
        registered = false;
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (idleGauge != null) {
            try {
                idleGauge.close();
            } catch (Exception ignored) {
            }
            idleGauge = null;
        }
        Set<UUID> onlineUuids = new HashSet<>();
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            UUID uuid = playerRef.getUuid();
            onlineUuids.add(uuid);
            PlayerIdleState state = playerStates.get(uuid);
            if (state != null) {
                restorePlayerSettings(playerRef, state);
            }
        }
        playerStates.keySet().removeIf(uuid -> !onlineUuids.contains(uuid));
    }

    private synchronized void evaluatePlayers() {
        IdlePlayerHandlerConfig cfg = IdlePlayerHandlerConfig.get();
        if (!registered || !cfg.getValue(IdlePlayerHandlerConfig.ENABLED)) {
            return;
        }

        long now = System.currentTimeMillis();
        long timeoutMs = Math.max(30, cfg.getValue(IdlePlayerHandlerConfig.IDLE_TIMEOUT_SECONDS)) * 1000L;
        double movementThreshold = cfg.getValue(IdlePlayerHandlerConfig.MOVEMENT_THRESHOLD);

        Set<UUID> onlineUuids = new HashSet<>();
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) {
                continue;
            }
            UUID uuid = playerRef.getUuid();
            onlineUuids.add(uuid);
            PlayerIdleState state = playerStates.computeIfAbsent(uuid, _u -> new PlayerIdleState());

            Vector3d currentPos = playerRef.getTransform().getPosition();

            // Detect movement
            if (state.lastPosition != null && hasPlayerMoved(state.lastPosition, currentPos, movementThreshold)) {
                state.markActivity();
                if (state.wasIdle) {
                    restorePlayerSettings(playerRef, state);
                }
            }
            if (state.lastPosition == null) {
                state.lastPosition = new Vector3d(currentPos);
            } else {
                state.lastPosition.set(currentPos);
            }

            if (!state.wasIdle && now - state.lastActivityMs > timeoutMs) {
                applyIdleSettings(playerRef, state, cfg);
            }
        }

        // Clean up disconnected players
        playerStates.keySet().removeIf(uuid -> !onlineUuids.contains(uuid));
    }

    public int getIdleCount() {
        int idle = 0;
        for (PlayerIdleState state : playerStates.values()) {
            if (state.wasIdle) {
                idle++;
            }
        }
        return idle;
    }

    public boolean isIdle(UUID uuid) {
        PlayerIdleState state = playerStates.get(uuid);
        return state != null && (state.wasIdle || state.pendingApply);
    }

    private void applyIdleSettings(PlayerRef playerRef, PlayerIdleState state, IdlePlayerHandlerConfig cfg) {
        if (state.pendingApply) {
            return;
        }
        World world = getPlayerWorld(playerRef);
        if (world == null) {
            return;
        }
        state.pendingApply = true;

        try {
            world.execute(() -> {
                synchronized (IdlePlayerService.this) {
                    try {
                        if (!registered || playerStates.get(playerRef.getUuid()) != state) {
                            return;
                        }
                        ChunkTracker tracker = playerRef.getChunkTracker();

                        if (cfg.getValue(IdlePlayerHandlerConfig.REDUCE_VIEW_RADIUS)) {
                            Player player = getPlayerComponent(playerRef);
                            if (player != null) {
                                int currentView = player.getClientViewRadius();
                                int idleView = Math.max(2, cfg.getValue(IdlePlayerHandlerConfig.IDLE_VIEW_RADIUS));
                                if (currentView > idleView) {
                                    if (state.savedViewRadius == null) {
                                        state.savedViewRadius = currentView;
                                    }
                                    player.setClientViewRadius(idleView);
                                }
                            }
                        }

                        if (cfg.getValue(IdlePlayerHandlerConfig.REDUCE_HOT_RADIUS)) {
                            int currentHot = tracker.getMaxHotLoadedRadius();
                            int idleHot = Math.max(2, cfg.getValue(IdlePlayerHandlerConfig.IDLE_HOT_RADIUS));
                            if (currentHot > idleHot) {
                                if (state.savedHotRadius == null) {
                                    state.savedHotRadius = currentHot;
                                }
                                tracker.setMaxHotLoadedRadius(idleHot);
                            }
                        }

                        if (cfg.getValue(IdlePlayerHandlerConfig.REDUCE_MIN_LOADED_RADIUS)) {
                            int currentMinLoaded = tracker.getMinLoadedRadius();
                            int idleMinLoaded =
                                    Math.max(2, cfg.getValue(IdlePlayerHandlerConfig.IDLE_MIN_LOADED_RADIUS));
                            if (currentMinLoaded > idleMinLoaded) {
                                if (state.savedMinLoadedRadius == null) {
                                    state.savedMinLoadedRadius = currentMinLoaded;
                                }
                                tracker.setMinLoadedRadius(idleMinLoaded);
                            }
                        }

                        state.wasIdle = true;
                        LOGGER.atInfo().log("Applied idle settings for player %s", playerRef.getUuid());
                        BlackboxBridge.count("IdlePlayer applied", 1);
                    } catch (Throwable t) {
                        LOGGER.atWarning().withCause(t).log(
                                "Failed to apply idle settings for player %s", playerRef.getUuid());
                    } finally {
                        state.pendingApply = false;
                    }
                }
            });
        } catch (RuntimeException e) {
            state.pendingApply = false;
            LOGGER.atWarning().withCause(e).log("Failed to queue idle settings for player %s", playerRef.getUuid());
        }
    }

    private void restorePlayerSettings(PlayerRef playerRef, PlayerIdleState state) {
        if (state.pendingRestore) {
            return;
        }
        World world = getPlayerWorld(playerRef);
        if (world == null) {
            return;
        }
        state.pendingRestore = true;

        try {
            world.execute(() -> {
                synchronized (IdlePlayerService.this) {
                    try {
                        ChunkTracker tracker = playerRef.getChunkTracker();

                        if (state.savedViewRadius != null) {
                            Player player = getPlayerComponent(playerRef);
                            if (player == null) {
                                return;
                            }
                            player.setClientViewRadius(state.savedViewRadius);
                            state.savedViewRadius = null;
                        }

                        if (state.savedHotRadius != null) {
                            tracker.setMaxHotLoadedRadius(state.savedHotRadius);
                            state.savedHotRadius = null;
                        }

                        if (state.savedMinLoadedRadius != null) {
                            tracker.setMinLoadedRadius(state.savedMinLoadedRadius);
                            state.savedMinLoadedRadius = null;
                        }

                        state.wasIdle = false;
                        if (!registered) {
                            playerStates.remove(playerRef.getUuid(), state);
                        }
                        LOGGER.atInfo().log("Restored settings for player %s", playerRef.getUuid());
                        BlackboxBridge.count("IdlePlayer restored", 1);
                    } catch (Throwable t) {
                        LOGGER.atWarning().withCause(t).log(
                                "Failed to restore settings for player %s", playerRef.getUuid());
                    } finally {
                        state.pendingRestore = false;
                    }
                }
            });
        } catch (RuntimeException e) {
            state.pendingRestore = false;
            LOGGER.atWarning().withCause(e).log(
                    "Failed to queue radius restoration for player %s", playerRef.getUuid());
        }
    }

    synchronized void restoreHotRadius(PlayerRef playerRef, int radius) {
        PlayerIdleState state = playerStates.get(playerRef.getUuid());
        if (state != null
                && (state.savedHotRadius != null
                        || (state.wasIdle
                                && IdlePlayerHandlerConfig.get()
                                        .getValue(IdlePlayerHandlerConfig.REDUCE_HOT_RADIUS)))) {
            state.savedHotRadius = radius;
        } else {
            playerRef.getChunkTracker().setMaxHotLoadedRadius(radius);
        }
    }

    private static World getPlayerWorld(PlayerRef playerRef) {
        Ref<EntityStore> entityRef = playerRef.getReference();
        if (entityRef == null || !entityRef.isValid()) {
            return null;
        }
        Store<EntityStore> store = entityRef.getStore();
        return store.getExternalData().getWorld();
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
        double dx = prev.x() - curr.x();
        double dz = prev.z() - curr.z();
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
