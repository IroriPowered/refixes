package cc.irori.refixes;

import cc.irori.refixes.config.impl.ListenerConfig;
import cc.irori.refixes.config.impl.RefixesConfig;
import cc.irori.refixes.config.impl.SystemConfig;
import cc.irori.refixes.listener.DefaultWorldWatcher;
import cc.irori.refixes.listener.InstancePositionTracker;
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
    protected void start() {}

    private void registerFixes() {
        fixSummary.clear();

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
