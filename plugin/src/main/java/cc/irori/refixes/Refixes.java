package cc.irori.refixes;

import cc.irori.refixes.config.impl.RefixesConfig;
import cc.irori.refixes.config.impl.SanitizerConfig;
import cc.irori.refixes.sanitizer.DefaultWorldWatcher;
import cc.irori.refixes.system.RespawnBlockFixSystem;
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

        applyFix("Default world watcher", SanitizerConfig.get().getValue(SanitizerConfig.DEFAULT_WORLD_WATCHER), () -> {
            defaultWorldWatcher = new DefaultWorldWatcher();
            defaultWorldWatcher.registerEvents(this);
        });
        applyFix(
                "Respawn block fix",
                SanitizerConfig.get().getValue(SanitizerConfig.RESPAWN_BLOCK),
                () -> getChunkStoreRegistry().registerSystem(new RespawnBlockFixSystem()));

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
