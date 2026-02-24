package cc.irori.refixes;

import cc.irori.refixes.config.impl.AiTickThrottlerConfig;
import cc.irori.refixes.config.impl.ChunkUnloaderConfig;
import cc.irori.refixes.config.impl.CylinderVisibilityConfig;
import cc.irori.refixes.config.impl.EarlyConfig;
import cc.irori.refixes.config.impl.IdlePlayerHandlerConfig;
import cc.irori.refixes.config.impl.KDTreeOptimizationConfig;
import cc.irori.refixes.config.impl.ListenerConfig;
import cc.irori.refixes.config.impl.PerPlayerHotRadiusConfig;
import cc.irori.refixes.config.impl.RefixesConfig;
import cc.irori.refixes.config.impl.SharedInstanceConfig;
import cc.irori.refixes.config.impl.SystemConfig;
import cc.irori.refixes.config.impl.TickSleepOptimizationConfig;
import cc.irori.refixes.config.impl.WatchdogConfig;
import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.TickSleepOptimization;
import cc.irori.refixes.listener.InstancePositionTracker;
import cc.irori.refixes.listener.SharedInstanceBootUnloader;
import cc.irori.refixes.service.ActiveChunkUnloader;
import cc.irori.refixes.service.AiTickThrottlerService;
import cc.irori.refixes.service.IdlePlayerService;
import cc.irori.refixes.service.PerPlayerHotRadiusService;
import cc.irori.refixes.service.WatchdogService;
import cc.irori.refixes.system.CraftingManagerFixSystem;
import cc.irori.refixes.system.EntityDespawnTimerSystem;
import cc.irori.refixes.system.InteractionManagerFixSystem;
import cc.irori.refixes.system.ProcessingBenchFixSystem;
import cc.irori.refixes.system.RespawnBlockFixSystem;
import cc.irori.refixes.system.SharedInstancePersistenceSystem;
import cc.irori.refixes.util.Early;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class Refixes extends JavaPlugin {

    private static final HytaleLogger LOGGER = Logs.logger();

    private final Config<RefixesConfig> config;
    private final List<String> fixSummary = new ArrayList<>();

    private InstancePositionTracker instancePositionTracker;
    private SharedInstanceBootUnloader sharedInstanceBootUnloader;

    private ActiveChunkUnloader activeChunkUnloader;
    private PerPlayerHotRadiusService perPlayerHotRadiusService;
    private WatchdogService watchdogService;

    private IdlePlayerService idlePlayerService;
    private AiTickThrottlerService aiTickThrottler;

    public Refixes(@NonNullDecl JavaPluginInit init) {
        super(init);
        config = withConfig(RefixesConfig.get().getCodec());
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
        if (watchdogService != null) {
            watchdogService.unregisterService();
        }
    }

    private void registerEarlyOptions() {
        EarlyConfig config = EarlyConfig.get();
        CylinderVisibilityConfig cylinderVisibilityConfig = CylinderVisibilityConfig.get();
        KDTreeOptimizationConfig kdTreeOptimizationConfig = KDTreeOptimizationConfig.get();
        SharedInstanceConfig sharedInstanceConfig = SharedInstanceConfig.get();

        EarlyOptions.MAX_CHUNKS_PER_SECOND.setSupplier(() -> config.getValue(EarlyConfig.MAX_CHUNKS_PER_SECOND));
        EarlyOptions.MAX_CHUNKS_PER_TICK.setSupplier(() -> config.getValue(EarlyConfig.MAX_CHUNKS_PER_TICK));

        EarlyOptions.CYLINDER_VISIBILITY_HEIGHT_MULTIPLIER.setSupplier(
                () -> cylinderVisibilityConfig.getValue(CylinderVisibilityConfig.HEIGHT_MULTIPLIER));

        EarlyOptions.KDTREE_OPTIMIZATION_THRESHOLD.setSupplier(
                () -> kdTreeOptimizationConfig.getValue(KDTreeOptimizationConfig.SPATIAL_FAST_SORT_THRESHOLD));

        EarlyOptions.SHARED_INSTANCES_EXCLUDED_PREFIXES.setSupplier(
                () -> sharedInstanceConfig.getValue(SharedInstanceConfig.EXCLUDED_PREFIXES));

        EarlyOptions.setAvailable(true);

        /* Tick Sleep Optimization */
        TickSleepOptimizationConfig tsoConfig = TickSleepOptimizationConfig.get();
        TickSleepOptimization.updateSleepOffset(
                tsoConfig.getValue(TickSleepOptimizationConfig.ENABLED),
                tsoConfig.getValue(TickSleepOptimizationConfig.SPIN_THRESHOLD_NANOS));
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

        // Systems
        applyFix(
                "Respawn block fix",
                SystemConfig.get().getValue(SystemConfig.RESPAWN_BLOCK),
                () -> getChunkStoreRegistry().registerSystem(new RespawnBlockFixSystem()));
        applyFix(
                "Processing bench fix",
                SystemConfig.get().getValue(SystemConfig.PROCESSING_BENCH),
                () -> getChunkStoreRegistry().registerSystem(new ProcessingBenchFixSystem()));
        applyFix(
                "Crafting manager fix",
                SystemConfig.get().getValue(SystemConfig.CRAFTING_MANAGER)
                        && Early.isEnabledLogging("Crafting manager fix"),
                () -> getEntityStoreRegistry().registerSystem(new CraftingManagerFixSystem()));
        applyFix(
                "Interaction manager fix",
                SystemConfig.get().getValue(SystemConfig.INTERACTION_MANAGER),
                () -> getEntityStoreRegistry().registerSystem(new InteractionManagerFixSystem()));
        applyFix(
                "Entity despawn timer",
                SystemConfig.get().getValue(SystemConfig.ENTITY_DESPAWN_TIMER),
                () -> getEntityStoreRegistry().registerSystem(new EntityDespawnTimerSystem()));

        // Services
        applyFix(
                "Active chunk unloader",
                ChunkUnloaderConfig.get().getValue(ChunkUnloaderConfig.ENABLED),
                () -> activeChunkUnloader = new ActiveChunkUnloader());
        applyFix(
                "Per-player hot radius",
                PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.ENABLED),
                () -> perPlayerHotRadiusService = new PerPlayerHotRadiusService());
        applyFix(
                "Server watchdog",
                WatchdogConfig.get().getValue(WatchdogConfig.ENABLED),
                () -> watchdogService = new WatchdogService());

        applyFix(
                "Idle player handler",
                IdlePlayerHandlerConfig.get().getValue(IdlePlayerHandlerConfig.ENABLED),
                () -> idlePlayerService = new IdlePlayerService());
        // Ai tick throttler will run once to sweep frozen entities and allow safe deinstall
        aiTickThrottler = new AiTickThrottlerService();
        fixSummary.add(
                AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.ENABLED)
                        ? "  - [x] AI tick throttler"
                        : "  - [ ] AI tick throttler");

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
}
