package cc.irori.refixes;

import cc.irori.refixes.command.ChunkLoaderCommand;
import cc.irori.refixes.compat.HyboxBridge;
import cc.irori.refixes.component.TickThrottled;
import cc.irori.refixes.config.impl.AiTickThrottlerConfig;
import cc.irori.refixes.config.impl.ChunkLoaderConfig;
import cc.irori.refixes.config.impl.CompatibilityConfig;
import cc.irori.refixes.config.impl.CorrectnessConfig;
import cc.irori.refixes.config.impl.CylinderVisibilityConfig;
import cc.irori.refixes.config.impl.DiagnosticsConfig;
import cc.irori.refixes.config.impl.EarlyConfig;
import cc.irori.refixes.config.impl.EntityDespawnTimerConfig;
import cc.irori.refixes.config.impl.FeaturesConfig;
import cc.irori.refixes.config.impl.IdlePlayerHandlerConfig;
import cc.irori.refixes.config.impl.IdleWorldPauseConfig;
import cc.irori.refixes.config.impl.KDTreeOptimizationConfig;
import cc.irori.refixes.config.impl.PerPlayerHotRadiusConfig;
import cc.irori.refixes.config.impl.RecoveryConfig;
import cc.irori.refixes.config.impl.RefixesConfig;
import cc.irori.refixes.config.impl.RuntimeConfig;
import cc.irori.refixes.config.impl.SharedInstanceConfig;
import cc.irori.refixes.config.impl.TickThrottledCleanupConfig;
import cc.irori.refixes.config.impl.TuningConfig;
import cc.irori.refixes.config.impl.UnknownBlockCleanerConfig;
import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.accessor.ChunkGridAccess;
import cc.irori.refixes.early.accessor.ChunkStoreAccess;
import cc.irori.refixes.early.util.PathfindingBudget;
import cc.irori.refixes.listener.ChunkLoaderWorldListener;
import cc.irori.refixes.listener.SharedInstanceBootUnloader;
import cc.irori.refixes.listener.UnknownBlockCleaner;
import cc.irori.refixes.service.AiTickThrottlerService;
import cc.irori.refixes.service.ChunkLoaderService;
import cc.irori.refixes.service.IdlePlayerService;
import cc.irori.refixes.service.IdleWorldPauseService;
import cc.irori.refixes.service.PerPlayerHotRadiusService;
import cc.irori.refixes.service.SharedInstanceStorage;
import cc.irori.refixes.service.WatchdogService;
import cc.irori.refixes.system.AiTickThrottlerCleanupSystem;
import cc.irori.refixes.system.ChunkLoaderSectionLifecycleSystem;
import cc.irori.refixes.system.ChunkLoaderSectionSystem;
import cc.irori.refixes.system.CraftingManagerFixSystem;
import cc.irori.refixes.system.EntityDespawnTimerSystem;
import cc.irori.refixes.system.SharedInstanceChunkSaveSkipSystem;
import cc.irori.refixes.system.SharedInstancePersistenceSystem;
import cc.irori.refixes.util.Early;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.Config;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class Refixes extends JavaPlugin {

    private static final HytaleLogger LOGGER = Logs.logger();

    private static Refixes instance;

    private final Config<RefixesConfig> config;
    private final List<String> fixSummary = new ArrayList<>();

    private ComponentType<EntityStore, TickThrottled> tickThrottledComponent;

    private SharedInstanceBootUnloader sharedInstanceBootUnloader;

    private PerPlayerHotRadiusService perPlayerHotRadiusService;
    private WatchdogService watchdogService;
    private IdlePlayerService idlePlayerService;
    private AiTickThrottlerService aiTickThrottler;
    private ChunkLoaderService chunkLoaderService;
    private IdleWorldPauseService idleWorldPauseService;
    private final java.nio.file.Path dataDirectory;
    private AutoCloseable pathfindingDeferralsGauge;

    public Refixes(@NonNullDecl JavaPluginInit init) {
        super(init);
        instance = this;
        dataDirectory = init.getDataDirectory();
        config = withConfig(RefixesConfig.get().getCodec());
    }

    @Override
    protected void setup() {
        if (Early.isEnabled()) EarlyOptions.SHARED_INSTANCES_STORAGE_AVAILABLE.setSupplier(() -> false);
        config.load().join();
        config.save().join();
        PerPlayerHotRadiusService.restoreSavedViewRadius();

        if (Early.isEnabled()) {
            if (!EarlyOptions.hasMixinSelection()) {
                throw new IllegalStateException(
                        "Refixes early mixin selection failed. Check mods/IroriPowered_Refixes/Refixes.json and the Hyinit log.");
            }
            if (EarlyOptions.isMixinEnabled("MixinInstancesPlugin")
                    && !CompatibilityConfig.get().getValue(CompatibilityConfig.EXISTING_SHARED_INSTANCES)) {
                throw new IllegalStateException(
                        "Shared-instance creation requires Runtime.Compatibility.ExistingSharedInstances");
            }
            try {
                registerEarlyOptions();
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log(
                        "Failed to pass config values to Refixes-Early, ensure that you have the same version of Refixes and Refixes-Early installed.");
            }
        }
        if (SharedInstanceConfig.get().getValue(SharedInstanceConfig.RESET_ON_EMPTY)
                && !(Early.isEnabled() && EarlyOptions.isMixinEnabled("MixinInstancesPlugin"))) {
            throw new IllegalStateException(
                    "Runtime.Features.SharedInstances.ResetOnEmpty requires Mixins.Features.SharedInstances");
        }

        registerComponents();
        registerFixes();
    }

    @Override
    protected void start() {
        if (perPlayerHotRadiusService != null) {
            perPlayerHotRadiusService.setIdlePlayerService(idlePlayerService);
            perPlayerHotRadiusService.registerService();
        }

        if (idlePlayerService != null) {
            idlePlayerService.registerService();
        }
        if (aiTickThrottler != null) {
            aiTickThrottler.registerService();
        }
        if (idleWorldPauseService != null) {
            idleWorldPauseService.registerService();
        }
        if (watchdogService != null) {
            watchdogService.registerService();
        }
        if (chunkLoaderService != null) {
            Universe.get().getWorlds().values().forEach(chunkLoaderService::loadWorld);
        }
        try {
            pathfindingDeferralsGauge = HyboxBridge.registerGauge(
                    "PathfindingBudget deferrals", () -> (double) PathfindingBudget.deferrals());
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void shutdown() {
        if (Early.isEnabled()) EarlyOptions.SHARED_INSTANCES_STORAGE_AVAILABLE.setSupplier(() -> false);
        UnknownBlockCleaner.shutdown();
        if (chunkLoaderService != null)
            chunkLoaderService.shutdown(HytaleServer.get().isShuttingDown());
        if (perPlayerHotRadiusService != null) {
            perPlayerHotRadiusService.unregisterService();
        }

        if (idlePlayerService != null) {
            idlePlayerService.unregisterService();
        }
        if (aiTickThrottler != null) {
            aiTickThrottler.unregisterService();
        }
        if (idleWorldPauseService != null) {
            idleWorldPauseService.unregisterService();
        }
        if (watchdogService != null) {
            watchdogService.unregisterService();
        }
        if (pathfindingDeferralsGauge != null) {
            try {
                pathfindingDeferralsGauge.close();
            } catch (Exception ignored) {
            }
            pathfindingDeferralsGauge = null;
        }
    }

    private void registerEarlyOptions() {
        EarlyConfig config = EarlyConfig.get();
        CylinderVisibilityConfig cylinderVisibilityConfig = CylinderVisibilityConfig.get();
        KDTreeOptimizationConfig kdTreeOptimizationConfig = KDTreeOptimizationConfig.get();
        SharedInstanceConfig sharedInstanceConfig = SharedInstanceConfig.get();

        EarlyOptions.MAX_SECTIONS_PER_SECOND.setSupplier(() -> config.getValue(EarlyConfig.MAX_SECTIONS_PER_SECOND));
        EarlyOptions.MAX_SECTIONS_PER_TICK.setSupplier(() -> config.getValue(EarlyConfig.MAX_SECTIONS_PER_TICK));
        EarlyOptions.CHUNK_UNLOAD_OFFSET.setSupplier(() -> config.getValue(EarlyConfig.UNLOAD_DISTANCE_OFFSET));
        EarlyOptions.VANILLA_KEEP_SPAWN_LOADED.setSupplier(
                () -> config.getValue(EarlyConfig.VANILLA_KEEP_SPAWN_LOADED));

        EarlyOptions.CYLINDER_VISIBILITY_HEIGHT_MULTIPLIER.setSupplier(
                () -> cylinderVisibilityConfig.getValue(CylinderVisibilityConfig.HEIGHT_MULTIPLIER));

        EarlyOptions.KDTREE_OPTIMIZATION_THRESHOLD.setSupplier(
                () -> kdTreeOptimizationConfig.getValue(KDTreeOptimizationConfig.SPATIAL_FAST_SORT_THRESHOLD));

        EarlyOptions.SHARED_INSTANCES_EXCLUDED_PREFIXES.setSupplier(
                () -> sharedInstanceConfig.getValue(SharedInstanceConfig.EXCLUDED_PREFIXES));
        EarlyOptions.SHARED_INSTANCES_RESET_ON_EMPTY.setSupplier(
                () -> sharedInstanceConfig.getValue(SharedInstanceConfig.RESET_ON_EMPTY));

        EarlyOptions.PATHFINDING_MAX_PATH_LENGTH.setSupplier(
                () -> config.getValue(EarlyConfig.PATHFINDING_MAX_PATH_LENGTH));
        EarlyOptions.PATHFINDING_OPEN_NODES_LIMIT.setSupplier(
                () -> config.getValue(EarlyConfig.PATHFINDING_OPEN_NODES_LIMIT));
        EarlyOptions.PATHFINDING_TOTAL_NODES_LIMIT.setSupplier(
                () -> config.getValue(EarlyConfig.PATHFINDING_TOTAL_NODES_LIMIT));
        EarlyOptions.PATHFINDING_MAX_NEW_SEARCHES_PER_TICK.setSupplier(
                () -> config.getValue(EarlyConfig.PATHFINDING_MAX_NEW_SEARCHES_PER_TICK));
        EarlyOptions.PATHFINDING_MAX_NODE_EXPANSIONS_PER_TICK.setSupplier(
                () -> config.getValue(EarlyConfig.PATHFINDING_MAX_NODE_EXPANSIONS_PER_TICK));
        EarlyOptions.SHUTDOWN_SAVE_TIMEOUT_SECONDS.setSupplier(
                () -> config.getValue(EarlyConfig.SHUTDOWN_SAVE_TIMEOUT_SECONDS));

        EarlyOptions.setAvailable(true);
    }

    private void registerComponents() {
        tickThrottledComponent = getEntityStoreRegistry()
                .registerComponent(TickThrottled.class, "Refixes_TickThrottled", TickThrottled.CODEC);
    }

    private void registerFixes() {
        fixSummary.clear();
        RuntimeConfig runtime = RuntimeConfig.get();
        TuningConfig tuning = runtime.getValue(RuntimeConfig.TUNING_CONFIG);
        FeaturesConfig features = runtime.getValue(RuntimeConfig.FEATURES_CONFIG);
        RecoveryConfig recovery = runtime.getValue(RuntimeConfig.RECOVERY_CONFIG);
        DiagnosticsConfig diagnostics = runtime.getValue(RuntimeConfig.DIAGNOSTICS_CONFIG);
        CompatibilityConfig compatibility = runtime.getValue(RuntimeConfig.COMPATIBILITY_CONFIG);

        // Listeners
        UnknownBlockCleanerConfig cleaner = recovery.getValue(RecoveryConfig.UNKNOWN_BLOCK_CLEANER_CONFIG);
        applyFix(
                "Unknown block cleaner",
                cleaner.getValue(UnknownBlockCleanerConfig.ENABLED),
                () -> UnknownBlockCleaner.registerEvents(this));

        // Systems
        CorrectnessConfig correctness = runtime.getValue(RuntimeConfig.CORRECTNESS_CONFIG);
        applyFix(
                "Crafting manager fix",
                correctness.getValue(CorrectnessConfig.CRAFTING_MANAGER),
                () -> getEntityStoreRegistry().registerSystem(new CraftingManagerFixSystem()));
        EntityDespawnTimerConfig despawn = tuning.getValue(TuningConfig.ENTITY_DESPAWN_TIMER_CONFIG);
        if (despawn.shouldRegisterSystem()) getEntityStoreRegistry().registerSystem(new EntityDespawnTimerSystem());
        TickThrottledCleanupConfig cleanup = compatibility.getValue(CompatibilityConfig.TICK_THROTTLED_CLEANUP_CONFIG);
        if (cleanup.isEnabled()) getEntityStoreRegistry().registerSystem(new AiTickThrottlerCleanupSystem());

        // Services
        applyFix(
                "Per-player hot radius",
                tuning.getValue(TuningConfig.PER_PLAYER_RADIUS_CONFIG).getValue(PerPlayerHotRadiusConfig.ENABLED),
                () -> perPlayerHotRadiusService = new PerPlayerHotRadiusService());
        applyFix(
                "Server watchdog",
                diagnostics.getValue(DiagnosticsConfig.WATCHDOG_CONFIG).shouldRegisterService(),
                () -> {
                    watchdogService = new WatchdogService();
                    watchdogService.registerEvents(this);
                });
        applyFix(
                "Idle player handler",
                tuning.getValue(TuningConfig.IDLE_PLAYER_HANDLER_CONFIG).getValue(IdlePlayerHandlerConfig.ENABLED),
                () -> idlePlayerService = new IdlePlayerService());
        applyFix(
                "AI tick throttler",
                tuning.getValue(TuningConfig.AI_TICK_THROTTLER_CONFIG).getValue(AiTickThrottlerConfig.ENABLED),
                () -> aiTickThrottler = new AiTickThrottlerService());
        applyFix(
                "Idle world pause",
                tuning.getValue(TuningConfig.IDLE_WORLD_PAUSE_CONFIG).getValue(IdleWorldPauseConfig.ENABLED),
                () -> idleWorldPauseService = new IdleWorldPauseService());
        if (Early.isEnabled() && compatibility.getValue(CompatibilityConfig.EXISTING_SHARED_INSTANCES)) {
            if (!EarlyOptions.isMixinEnabled("MixinChunkGridAccess")
                    || !EarlyOptions.isMixinEnabled("MixinChunkStoreAccess")) {
                throw new IllegalStateException(
                        "Existing shared-instance storage requires Mixins.Features.SharedInstanceStorage");
            }
            if (!ChunkGridAccess.class.isAssignableFrom(ChunkStore.class)
                    || !ChunkStoreAccess.class.isAssignableFrom(ChunkStore.class)) {
                throw new IllegalStateException(
                        "Shared early classes do not match. Update Hyinit and Refixes together.");
            }
            SharedInstanceStorage.registerEvents(this);
            getChunkStoreRegistry().registerSystem(new SharedInstancePersistenceSystem());
            getChunkStoreRegistry().registerSystem(new SharedInstanceChunkSaveSkipSystem());
            sharedInstanceBootUnloader = new SharedInstanceBootUnloader();
            sharedInstanceBootUnloader.registerEvents(this);
            EarlyOptions.SHARED_INSTANCES_STORAGE_AVAILABLE.setSupplier(() -> true);
        }
        ChunkLoaderConfig chunkLoader = features.getValue(FeaturesConfig.CHUNK_LOADER_CONFIG);
        if (chunkLoader.getValue(ChunkLoaderConfig.ENABLED)) {
            chunkLoaderService = new ChunkLoaderService(dataDirectory);
            getCommandRegistry().registerCommand(new ChunkLoaderCommand(chunkLoaderService));
            getChunkStoreRegistry().registerSystem(new ChunkLoaderSectionSystem(chunkLoaderService));
            getChunkStoreRegistry().registerSystem(new ChunkLoaderSectionLifecycleSystem(chunkLoaderService));
            getChunkStoreRegistry().registerSystem(new ChunkLoaderSectionLifecycleSystem.Changes(chunkLoaderService));
            getChunkStoreRegistry().registerSystem(new ChunkLoaderSectionLifecycleSystem.Stores(chunkLoaderService));
            new ChunkLoaderWorldListener(chunkLoaderService).registerEvents(this);
            getEventRegistry().registerGlobal(EventPriority.LAST, RemoveWorldEvent.class, event -> {
                if (!event.isCancelled()) chunkLoaderService.unloadWorld(event.getWorld());
            });
        }
        LOGGER.atInfo().log("=== Refixes runtime patches ===");
        for (String summary : fixSummary) LOGGER.atInfo().log(summary);
    }

    private void applyFix(String name, boolean apply, Runnable fix) {
        if (apply) {
            try {
                fix.run();
                fixSummary.add("  - [x] " + name);
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log("Failed to apply fix: " + name);
                fixSummary.add("  - [ ] " + name + " (FAILED)");
            }
        } else {
            fixSummary.add("  - [ ] " + name);
        }
    }

    public ComponentType<EntityStore, TickThrottled> getTickThrottledComponent() {
        return tickThrottledComponent;
    }

    public ChunkLoaderService getChunkLoaderService() {
        return chunkLoaderService;
    }

    public static Refixes get() {
        return instance;
    }
}
