package cc.irori.refixes;

import cc.irori.refixes.config.impl.*;
import cc.irori.refixes.listener.DefaultWorldWatcher;
import cc.irori.refixes.listener.InstancePositionTracker;
import cc.irori.refixes.service.PerPlayerHotRadiusService;
import cc.irori.refixes.service.TpsAdjuster;
import cc.irori.refixes.system.CraftingManagerFixSystem;
import cc.irori.refixes.system.InteractionManagerFixSystem;
import cc.irori.refixes.system.ProcessingBenchFixSystem;
import cc.irori.refixes.system.RespawnBlockFixSystem;
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

    private DefaultWorldWatcher defaultWorldWatcher;
    private InstancePositionTracker instancePositionTracker;

    private PerPlayerHotRadiusService perPlayerHotRadiusService;
    private TpsAdjuster tpsAdjuster;

    public Refixes(@NonNullDecl JavaPluginInit init) {
        super(init);
        config = withConfig(RefixesConfig.get().getCodec());
    }

    @Override
    protected void setup() {
        config.load().join();
        config.save().join();

        registerFixes();
    }

    @Override
    protected void start() {
        if (perPlayerHotRadiusService != null) {
            perPlayerHotRadiusService.registerService();
        }
        if (tpsAdjuster != null) {
            tpsAdjuster.registerService();
        }
    }

    @Override
    protected void shutdown() {
        if (perPlayerHotRadiusService != null) {
            perPlayerHotRadiusService.unregisterService();
        }
        if (tpsAdjuster != null) {
            tpsAdjuster.unregisterService();
        }
    }

    private void registerFixes() {
        fixSummary.clear();

        // Listeners
        applyFix("Default world watcher", ListenerConfig.get().getValue(ListenerConfig.DEFAULT_WORLD_WATCHER), () -> {
            defaultWorldWatcher = new DefaultWorldWatcher();
            defaultWorldWatcher.registerEvents(this);
        });
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

        // Services
        applyFix(
                "Per-player hot radius",
                PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.ENABLED),
                () -> perPlayerHotRadiusService = new PerPlayerHotRadiusService());
        applyFix(
                "TPS adjuster",
                TpsAdjusterConfig.get().getValue(TpsAdjusterConfig.ENABLED),
                () -> tpsAdjuster = new TpsAdjuster());

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
