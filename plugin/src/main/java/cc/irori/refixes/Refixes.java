package cc.irori.refixes;

import cc.irori.refixes.command.ChunkLoaderCommand;
import cc.irori.refixes.component.TickThrottled;
import cc.irori.refixes.config.impl.AiTickThrottlerConfig;
import cc.irori.refixes.config.impl.ChunkUnloaderConfig;
import cc.irori.refixes.config.impl.CylinderVisibilityConfig;
import cc.irori.refixes.config.impl.EarlyConfig;
import cc.irori.refixes.config.impl.ExperimentalConfig;
import cc.irori.refixes.config.impl.IdlePlayerHandlerConfig;
import cc.irori.refixes.config.impl.IdleWorldPauseConfig;
import cc.irori.refixes.config.impl.KDTreeOptimizationConfig;
import cc.irori.refixes.config.impl.ListenerConfig;
import cc.irori.refixes.config.impl.PerPlayerHotRadiusConfig;
import cc.irori.refixes.config.impl.RefixesConfig;
import cc.irori.refixes.config.impl.SharedInstanceConfig;
import cc.irori.refixes.config.impl.SystemConfig;
import cc.irori.refixes.config.impl.TickSleepOptimizationConfig;
import cc.irori.refixes.config.impl.WatchdogConfig;
import cc.irori.refixes.copychunks.CopyChunksCommand;
import cc.irori.refixes.copychunks.PasteChunksCommand;
import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.TickSleepOptimization;
import cc.irori.refixes.listener.ChunkLoaderWorldListener;
import cc.irori.refixes.listener.InstancePositionTracker;
import cc.irori.refixes.listener.SharedInstanceBootUnloader;
import cc.irori.refixes.listener.UnknownBlockCleaner;
import cc.irori.refixes.service.ActiveChunkUnloader;
import cc.irori.refixes.service.AiTickThrottlerService;
import cc.irori.refixes.service.ChunkLoaderService;
import cc.irori.refixes.service.IdlePlayerService;
import cc.irori.refixes.service.IdleWorldPauseService;
import cc.irori.refixes.service.PerPlayerHotRadiusService;
import cc.irori.refixes.service.WatchdogService;
import cc.irori.refixes.system.AiTickThrottlerCleanupSystem;
import cc.irori.refixes.system.CraftingManagerFixSystem;
import cc.irori.refixes.system.EntityDespawnTimerSystem;
import cc.irori.refixes.system.RespawnBlockFixSystem;
import cc.irori.refixes.system.SharedInstancePersistenceSystem;
import cc.irori.refixes.util.Early;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
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

    private InstancePositionTracker instancePositionTracker;
    private SharedInstanceBootUnloader sharedInstanceBootUnloader;

    private ActiveChunkUnloader activeChunkUnloader;
    private PerPlayerHotRadiusService perPlayerHotRadiusService;
    private WatchdogService watchdogService;

    private IdlePlayerService idlePlayerService;
    private AiTickThrottlerService aiTickThrottler;
    private IdleWorldPauseService idleWorldPauseService;
    private ChunkLoaderService chunkLoaderService;

    public Refixes(@NonNullDecl JavaPluginInit init) {
        super(init);
        instance = this;
        config = withConfig(RefixesConfig.get().getCodec());
        chunkLoaderService = new ChunkLoaderService(init.getDataDirectory());
    }

    @Override
    protected void setup() {
        config.load().join();
        config.save().join();

        if (Early.isEnabled()) {
            try {
                registerEarlyOptions();
            } catch (Exception e) {
                LOGGER.atSevere().withCause(e).log(
                        "Failed to pass config values to Refixes-Early, ensure that you have the same version of Refixes and Refixes-Early installed.");
            }
        }

        registerComponents();
        registerFixes();
    }

    @Override
    protected void start() {
        if (activeChunkUnloader != null) {
            activeChunkUnloader.registerService();
        }
        if (perPlayerHotRadiusService != null) {
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
    }

    @Override
    protected void shutdown() {
        if (activeChunkUnloader != null) {
            activeChunkUnloader.unregisterService();
        }
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
    }

    private void registerEarlyOptions() {
        EarlyConfig config = EarlyConfig.get();
        CylinderVisibilityConfig cylinderVisibilityConfig = CylinderVisibilityConfig.get();
        KDTreeOptimizationConfig kdTreeOptimizationConfig = KDTreeOptimizationConfig.get();
        SharedInstanceConfig sharedInstanceConfig = SharedInstanceConfig.get();
        ExperimentalConfig experimentalConfig = ExperimentalConfig.get();

        if (config.getValue(EarlyConfig.FORCE_SKIP_MOD_VALIDATION)) {
            LOGGER.atSevere().log(
                    "Force Skip Mod Validation is enabled! ALWAYS remember to check your mods are working correctly after server updates.");
        }

        EarlyOptions.FORCE_SKIP_MOD_VALIDATION.setSupplier(
                () -> config.getValue(EarlyConfig.FORCE_SKIP_MOD_VALIDATION));
        EarlyOptions.MAX_CHUNKS_PER_SECOND.setSupplier(() -> config.getValue(EarlyConfig.MAX_CHUNKS_PER_SECOND));
        EarlyOptions.MAX_CHUNKS_PER_TICK.setSupplier(() -> config.getValue(EarlyConfig.MAX_CHUNKS_PER_TICK));
        EarlyOptions.CHUNK_UNLOAD_OFFSET.setSupplier(
                () -> ChunkUnloaderConfig.get().getValue(ChunkUnloaderConfig.UNLOAD_DISTANCE_OFFSET));
        EarlyOptions.VANILLA_KEEP_SPAWN_LOADED.setSupplier(
                () -> config.getValue(EarlyConfig.VANILLA_KEEP_SPAWN_LOADED));

        EarlyOptions.CYLINDER_VISIBILITY_HEIGHT_MULTIPLIER.setSupplier(
                () -> cylinderVisibilityConfig.getValue(CylinderVisibilityConfig.HEIGHT_MULTIPLIER));

        EarlyOptions.KDTREE_OPTIMIZATION_THRESHOLD.setSupplier(
                () -> kdTreeOptimizationConfig.getValue(KDTreeOptimizationConfig.SPATIAL_FAST_SORT_THRESHOLD));

        EarlyOptions.SHARED_INSTANCES_EXCLUDED_PREFIXES.setSupplier(
                () -> sharedInstanceConfig.getValue(SharedInstanceConfig.EXCLUDED_PREFIXES));

        EarlyOptions.PARALLEL_STEERING_THRESHOLD.setSupplier(
                () -> experimentalConfig.getValue(ExperimentalConfig.PARALLEL_STEERING_THRESHOLD));

        EarlyOptions.STAT_RECALC_INTERVAL.setSupplier(() -> config.getValue(EarlyConfig.STAT_RECALC_INTERVAL));

        EarlyOptions.PATHFINDING_MAX_PATH_LENGTH.setSupplier(
                () -> config.getValue(EarlyConfig.PATHFINDING_MAX_PATH_LENGTH));
        EarlyOptions.PATHFINDING_OPEN_NODES_LIMIT.setSupplier(
                () -> config.getValue(EarlyConfig.PATHFINDING_OPEN_NODES_LIMIT));
        EarlyOptions.PATHFINDING_TOTAL_NODES_LIMIT.setSupplier(
                () -> config.getValue(EarlyConfig.PATHFINDING_TOTAL_NODES_LIMIT));

        EarlyOptions.setAvailable(true);

        /* Tick Sleep Optimization */
        TickSleepOptimizationConfig tsoConfig = TickSleepOptimizationConfig.get();
        TickSleepOptimization.updateSleepOffset(
                tsoConfig.getValue(TickSleepOptimizationConfig.ENABLED),
                tsoConfig.getValue(TickSleepOptimizationConfig.SPIN_THRESHOLD_NANOS));
    }

    private void registerComponents() {
        tickThrottledComponent = getEntityStoreRegistry()
                .registerComponent(TickThrottled.class, "Refixes_TickThrottled", TickThrottled.CODEC);
    }

    private void registerFixes() {
        fixSummary.clear();

        // Listeners
        applyFix(
                "Instance position tracker",
                ListenerConfig.get().getValue(ListenerConfig.INSTANCE_POSITION_TRACKER),
                () -> {
                    instancePositionTracker = new InstancePositionTracker();
                    instancePositionTracker.registerEvents(this);
                });
        applyFix(
                "Unknown block cleaner",
                ListenerConfig.get().getValue(ListenerConfig.UNKNOWN_BLOCK_CLEANER),
                () -> UnknownBlockCleaner.registerEvents(this));

        // Systems
        applyFix(
                "Respawn block fix",
                SystemConfig.get().getValue(SystemConfig.RESPAWN_BLOCK),
                () -> getChunkStoreRegistry().registerSystem(new RespawnBlockFixSystem()));
        applyFix(
                "Crafting manager fix",
                SystemConfig.get().getValue(SystemConfig.CRAFTING_MANAGER)
                        && Early.isEnabledLogging("Crafting manager fix"),
                () -> getEntityStoreRegistry().registerSystem(new CraftingManagerFixSystem()));
        applyFix(
                "Entity despawn timer",
                SystemConfig.get().getValue(SystemConfig.ENTITY_DESPAWN_TIMER),
                () -> getEntityStoreRegistry().registerSystem(new EntityDespawnTimerSystem()));
        getEntityStoreRegistry().registerSystem(new AiTickThrottlerCleanupSystem());

        // Services
        applyFix(
                "Active chunk unloader",
                ChunkUnloaderConfig.get().getValue(ChunkUnloaderConfig.ENABLED),
                () -> activeChunkUnloader = new ActiveChunkUnloader());
        applyFix(
                "Per-player hot radius",
                PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.ENABLED),
                () -> perPlayerHotRadiusService = new PerPlayerHotRadiusService());
        applyFix("Server watchdog", WatchdogConfig.get().getValue(WatchdogConfig.ENABLED), () -> {
            watchdogService = new WatchdogService();
            watchdogService.registerEvents(this);
        });

        applyFix(
                "Idle player handler",
                IdlePlayerHandlerConfig.get().getValue(IdlePlayerHandlerConfig.ENABLED),
                () -> idlePlayerService = new IdlePlayerService());
        applyFix(
                "AI tick throttler",
                AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.ENABLED),
                () -> aiTickThrottler = new AiTickThrottlerService());
        applyFix(
                "Idle world pause",
                IdleWorldPauseConfig.get().getValue(IdleWorldPauseConfig.ENABLED),
                () -> idleWorldPauseService = new IdleWorldPauseService());

        getCommandRegistry().registerCommand(new ChunkLoaderCommand(chunkLoaderService));
        getCommandRegistry().registerCommand(new CopyChunksCommand());
        getCommandRegistry().registerCommand(new PasteChunksCommand());

        new ChunkLoaderWorldListener(chunkLoaderService).registerEvents(this);

        applyFix(
                "Shared instance worlds",
                SharedInstanceConfig.get().getValue(SharedInstanceConfig.ENABLED)
                        && Early.isEnabledLogging("Shared instance worlds"),
                () -> {
                    getChunkStoreRegistry().registerSystem(new SharedInstancePersistenceSystem());
                    sharedInstanceBootUnloader = new SharedInstanceBootUnloader();
                    sharedInstanceBootUnloader.registerEvents(this);
                });

        LOGGER.atInfo().log("=== Refixes runtime patches ===");
        for (String summary : fixSummary) {
            LOGGER.atInfo().log(summary);
        }
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

    public static Refixes get() {
        return instance;
    }
}
