package cc.irori.refixes.config.impl;

import cc.irori.refixes.component.TickThrottled;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.util.Config;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.BsonDocument;

public final class ConfigMigrationRegression {
    private static final String PLUGIN = "cc.irori.refixes.early.RefixesMixinPlugin";
    private static final String JFR_INSTEAD =
            "(Refixes records JDK Flight Recorder events instead, controlled by Runtime.Diagnostics.JfrEvents.)";

    public static void main(String[] args) throws Exception {
        require(!UnknownBlockCleanerConfig.get().getValue(UnknownBlockCleanerConfig.ENABLED), "Destructive cleaner enabled by default");
        require(!TickThrottledCleanupConfig.get().getValue(TickThrottledCleanupConfig.LEGACY_CLEANUP), "Unowned legacy cleanup enabled by default");
        require(DiagnosticsConfig.get().getValue(DiagnosticsConfig.JFR_EVENTS), "JFR events disabled by default");
        Path data = Path.of("mods/IroriPowered_Refixes");
        Files.createDirectories(data);
        Files.writeString(data.resolve("Refixes.json"), """
                {
                  "Mixins": {
                    "Optimizations": {"CollectVisible": true, "KDTree": true, "FluidPlugin": true, "AStarBase": false, "RepulsionNeighbours": true, "NPCComponentType": true},
                    "Experimental": {"ShutdownSaveTimeout": true, "TickSurvival": false, "ParallelEntityTicking": false, "Parallel": {"CollectVisible": false, "AllSystems": true}},
                    "Crashfixes": {"BlockSectionSafety": true, "CollisionModule": false, "MountPlugin": false, "RepulsionTicker": false, "Player": true},
                    "Helpers": {"WorldMapTracker": true, "TickSleep": false, "World": false, "MarkerAddRemoveSystem": false},
                    "Correctness": {"QuicheChannel": false, "WorldTransfer": false},
                    "Tuning": {"WorldHeightBounds": true},
                    "Backports": {"ShutdownRemoval": true, "ShutdownSaveFailure": false, "StaticLayers": true, "ModelParticleScale": true}
                  },
                  "HypixelServices": {"LiveConfig": true}
                }
                """);
        Files.writeString(data.resolve("config.json"), """
                {
                  "BlackboxIntegration": false,
                  "Early": {"MaxSectionsPerTick": 12, "MaxChunksPerSecond": 36, "BackpressureMaxOutboundBytes": 1024, "KDTreeOptimization": {"SpatialFastSortThreshold": 32}},
                  "Listeners": {"UnknownBlockCleaner": true, "UnknownBlockCleanerScanFluids": true, "UnknownBlockCleanerBudgetMs": 7},
                  "Systems": {"CraftingManager": false, "EntityDespawnTimer": false, "EntityDespawnTimerConfig": {"ItemDespawnSeconds": 99, "AddTimerWhenMissing": true}},
                  "Services": {
                    "PerPlayerHotRadius": {"Enabled": false, "MaxRadius": 6, "TPSLow": 22.5},
                    "ChunkUnloader": {"Enabled": false},
                    "AiTickThrottler": {"Enabled": true, "NearChunks": 3, "CleanupFrozenEntities": false, "LegacyCleanup": true},
                    "IdleWorldPause": {"ExcludedWorlds": ["lobby"]}
                  },
                  "ChunkLoader": {"Enabled": false},
                  "SharedInstanceWorlds": {"ExcludedPrefixes": ["keep-"]},
                  "Watchdog": {"Enabled": false, "AutoRestartWorlds": true, "ThreadTimeoutMs": 45000},
                  "Experimental": {"ParallelSteeringThreshold": 32}
                }
                """);

        String log = load();
        JsonObject mixins = read(data.resolve("Refixes.json")).getAsJsonObject("Mixins");
        expect(mixins, "Tuning.CylinderVisibility", false);
        expect(mixins, "Recovery.ShutdownSaveTimeout", true);
        expect(mixins, "Recovery.CorruptSectionReset", false);
        expect(mixins, "Recovery.InvalidCollisionData", false);
        expect(mixins, "Correctness.MountPlugin", false);
        expect(mixins, "Tuning.TickWait", false);
        expect(mixins, "Correctness.MarkerAddRemoveSystem", false);
        expect(mixins, "Compatibility.DisableLiveConfig", true);
        for (String split : new String[] {"OutgoingQueueLimits", "SetupPacing", "QuicCloseRetention", "ChunkSectionFences"})
            expect(mixins, "Correctness." + split, false);
        for (String split : new String[] {"WorldTransfer", "StorageOrdering", "TransferCombatClock"})
            expect(mixins, "Correctness." + split, false);
        require(!mixins.getAsJsonObject("Recovery").has("WorldMapTracker"), "Retired WorldMapTracker toggle remains");
        require(!mixins.getAsJsonObject("Correctness").has("WorldMapCache"), "Retired WorldMapCache toggle remains");
        require(!mixins.getAsJsonObject("Optimizations").has("KDTree"), "Retired KDTree toggle remains");
        require(!mixins.getAsJsonObject("Optimizations").has("NPCComponentType"), "Retired NPCComponentType toggle remains");
        expect(mixins, "Experimental.Parallel.CollectVisible", false);
        expect(mixins, "Optimizations.RepulsionNeighbors", true);
        expect(mixins, "Optimizations.RepulsionTicker", false);
        require(!mixins.getAsJsonObject("Optimizations").has("RepulsionNeighbours"), "Renamed RepulsionNeighbours toggle remains");
        require(!mixins.has("Crashfixes") && !mixins.has("Helpers") && !mixins.has("Backports"), "Retired categories remain");
        require(!mixins.getAsJsonObject("Experimental").getAsJsonObject("Parallel").has("AllSystems"), "Retired toggle remains");
        require(!mixins.getAsJsonObject("Correctness").has("QuicheChannel"), "Split toggle remains");
        require(!read(data.resolve("Refixes.json")).has("HypixelServices"), "Migrated legacy root remains");
        for (String line : new String[] {
            "Removed retired setting Mixins.Optimizations.KDTree=true (no measurable gain)",
            "Migrated Mixins.Experimental.ShutdownSaveTimeout=true to Mixins.Recovery.ShutdownSaveTimeout",
            "Migrated Mixins.Optimizations.RepulsionNeighbours=true to Mixins.Optimizations.RepulsionNeighbors",
            "Removed Mixins.Crashfixes.BlockSectionSafety=true (old default)",
            "Removed retired setting Mixins.Optimizations.FluidPlugin=true",
            "Removed retired setting Mixins.Experimental.Parallel.AllSystems=true",
            "Removed retired setting Mixins.Helpers.World=false",
            "Removed retired setting Mixins.Helpers.WorldMapTracker=true (fixed in Hytale 0.8.0-pre.5)",
            "Removed retired setting Mixins.Tuning.WorldHeightBounds=true (the height check it changed no longer exists in Hytale 0.8.0-pre.5)",
            "Removed retired setting Mixins.Optimizations.NPCComponentType=true (the NPC type lookup it cached no longer exists in Hytale 0.8.0-pre.6)",
            "Removed retired setting Mixins.Backports.ShutdownRemoval=true (fixed in Hytale 0.8.0-pre.6)",
            "Removed retired setting Mixins.Backports.ShutdownSaveFailure=false (fixed in Hytale 0.8.0-pre.6)",
            "Removed retired setting Mixins.Backports.StaticLayers=true (fixed in Hytale 0.8.0-pre.6)",
            "Removed retired setting Mixins.Backports.ModelParticleScale=true (fixed in Hytale 0.8.0-pre.6)",
            "Migrated Mixins.Correctness.QuicheChannel=false to Mixins.Correctness.OutgoingQueueLimits",
            "Removed retired setting Early.BackpressureMaxOutboundBytes=1024",
            "Removed retired setting Early.KDTreeOptimization={\"SpatialFastSortThreshold\":32} (no measurable gain)",
            "Removed retired setting Experimental.ParallelSteeringThreshold=32",
            "Migrated Services.AiTickThrottler.LegacyCleanup to Runtime.Compatibility.TickThrottledCleanup.LegacyCleanup",
            "Removed Services.AiTickThrottler.CleanupFrozenEntities=false (old default)",
            "Watchdog.Enabled=false",
            "Removed retired setting BlackboxIntegration=false " + JFR_INSTEAD,
            "Migrated Mixins.Crashfixes.RepulsionTicker=false to Mixins.Optimizations.RepulsionTicker",
            "Removed retired setting Mixins.Crashfixes.Player=true (removed)",
            "Removed retired setting Mixins.Experimental.ParallelEntityTicking=false (parallel ticking removed)",
            "Removed retired setting Early.MaxChunksPerSecond=36 (replaced by Early.MaxSectionsPerSecond)",
            "Removed retired setting Services.PerPlayerHotRadius.TPSLow=22.5 (replaced by TPSLowFraction)",
            "Removed retired setting Services.ChunkUnloader={\"Enabled\":false} (removed)"
        }) require(log.contains(line), "Missing migration log line: " + line + "\n" + log);

        JsonObject runtimeJson = read(data.resolve("config.json"));
        for (String root : new String[] {"BlackboxIntegration", "Listeners", "Systems", "Services", "ChunkLoader", "SharedInstanceWorlds", "Watchdog", "Experimental"})
            require(!runtimeJson.has(root), "Legacy root section remains: " + root);
        require(!runtimeJson.getAsJsonObject("Early").has("BackpressureMaxOutboundBytes"), "Retired early key remains");
        require(!runtimeJson.getAsJsonObject("Early").has("KDTreeOptimization"), "Retired early section remains");

        Config<RefixesConfig> config = new Config<>(data, "config", RefixesConfig.get().getCodec());
        config.load().join();
        require(EarlyConfig.get().getValue(EarlyConfig.MAX_SECTIONS_PER_TICK) == 12, "Early key lost");
        UnknownBlockCleanerConfig cleaner = UnknownBlockCleanerConfig.get();
        require(cleaner.getValue(UnknownBlockCleanerConfig.ENABLED), "Cleaner enable not migrated");
        require(cleaner.getValue(UnknownBlockCleanerConfig.SCAN_FLUIDS), "Cleaner fluids not migrated");
        require(cleaner.getValue(UnknownBlockCleanerConfig.BUDGET_MS) == 7, "Cleaner budget not migrated");
        require(!CorrectnessConfig.get().getValue(CorrectnessConfig.CRAFTING_MANAGER), "CraftingManager not migrated");
        EntityDespawnTimerConfig despawn = EntityDespawnTimerConfig.get();
        require(despawn.getValue(EntityDespawnTimerConfig.ITEM_DESPAWN_SECONDS) == 99, "Despawn seconds not migrated");
        require(!despawn.shouldRegisterSystem(), "Disabled despawn timer was turned on");
        require(!PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.ENABLED), "Hot radius enable not migrated");
        require(PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.MAX_RADIUS) == 6, "Hot radius max not migrated");
        require(AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.ENABLED), "Throttler enable not migrated");
        require(AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.NEAR_CHUNKS) == 3, "Throttler chunks not migrated");
        TickThrottledCleanupConfig cleanup = TickThrottledCleanupConfig.get();
        require(cleanup.getValue(TickThrottledCleanupConfig.CLEANUP_FROZEN_ENTITIES), "Cleanup default not kept");
        require(cleanup.getValue(TickThrottledCleanupConfig.LEGACY_CLEANUP), "Legacy cleanup not migrated");
        require(IdleWorldPauseConfig.get().getValue(IdleWorldPauseConfig.EXCLUDED_WORLDS)[0].equals("lobby"), "Idle pause list not migrated");
        require(!ChunkLoaderConfig.get().getValue(ChunkLoaderConfig.ENABLED), "Chunk loader not migrated");
        require(SharedInstanceConfig.get().getValue(SharedInstanceConfig.EXCLUDED_PREFIXES)[0].equals("keep-"), "Shared prefixes not migrated");
        require(!WatchdogConfig.get().getValue(WatchdogConfig.ENABLED), "Watchdog enable not migrated");
        require(WatchdogConfig.get().getValue(WatchdogConfig.THREAD_TIMEOUT_MS) == 45000, "Watchdog timeout not migrated");
        WatchdogRecoveryConfig recovery = WatchdogRecoveryConfig.get();
        require(!recovery.getValue(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH), "Disabled watchdog still shuts down");
        require(!recovery.getValue(WatchdogRecoveryConfig.AUTO_RESTART_WORLDS), "Disabled watchdog still restarts worlds");
        require(!WatchdogConfig.get().shouldRegisterService(), "Disabled 0.5.0 watchdog still starts");

        String refixes = Files.readString(data.resolve("Refixes.json"));
        String runtime = Files.readString(data.resolve("config.json"));
        String second = load();
        require(!second.contains("Migrated") && !second.contains("Removed"), "Migration is not idempotent:\n" + second);
        require(Files.readString(data.resolve("Refixes.json")).equals(refixes), "Second start rewrote Refixes.json");
        require(Files.readString(data.resolve("config.json")).equals(runtime), "Second start rewrote config.json");

        Files.writeString(data.resolve("Refixes.json"), """
                {"Mixins": {"Optimizations": {"KDTree": false}}}
                """);
        String current = load();
        require(current.contains("Removed retired setting Mixins.Optimizations.KDTree=false (no measurable gain)"),
                "Current-format KDTree=false was not removed:\n" + current);
        require(!read(data.resolve("Refixes.json")).getAsJsonObject("Mixins").getAsJsonObject("Optimizations").has("KDTree"),
                "Current-format KDTree toggle remains");

        Files.writeString(data.resolve("config.json"), """
                {"HyboxIntegration": false, "Runtime": {"Diagnostics": {"HyboxIntegration": true, "Watchdog": {"Enabled": false}}}}
                """);
        String hybox = load();
        for (String line : new String[] {
            "Removed retired setting HyboxIntegration=false " + JFR_INSTEAD,
            "Removed retired setting Runtime.Diagnostics.HyboxIntegration=true " + JFR_INSTEAD
        }) require(hybox.contains(line), "Missing retirement log line: " + line + "\n" + hybox);
        JsonObject hyboxJson = read(data.resolve("config.json"));
        require(!hyboxJson.has("HyboxIntegration"), "Retired HyboxIntegration root remains");
        JsonObject diagnostics = hyboxJson.getAsJsonObject("Runtime").getAsJsonObject("Diagnostics");
        require(!diagnostics.has("HyboxIntegration"), "Retired Runtime.Diagnostics.HyboxIntegration remains");
        expect(diagnostics, "Watchdog.Enabled", false);
        require(!diagnostics.has("JfrEvents"), "HyboxIntegration=true migrated into JfrEvents");
        new Config<>(data, "config", RefixesConfig.get().getCodec()).load().join();
        require(DiagnosticsConfig.get().getValue(DiagnosticsConfig.JFR_EVENTS), "JfrEvents not at its default true");
        String retiredAgain = load();
        require(!retiredAgain.contains("HyboxIntegration"), "Retired HyboxIntegration logged twice:\n" + retiredAgain);
        Files.writeString(data.resolve("config.json"), """
                {"Runtime": {"Diagnostics": {"HyboxIntegration": false}}}
                """);
        String hyboxOff = load();
        String offLine = "Removed retired setting Runtime.Diagnostics.HyboxIntegration=false " + JFR_INSTEAD;
        require(hyboxOff.contains(offLine), "Missing retirement log line: " + offLine + "\n" + hyboxOff);
        JsonObject offDiagnostics = read(data.resolve("config.json")).getAsJsonObject("Runtime").getAsJsonObject("Diagnostics");
        require(!offDiagnostics.has("HyboxIntegration"), "Retired HyboxIntegration=false remains");
        require(!offDiagnostics.has("JfrEvents"), "HyboxIntegration=false migrated into JfrEvents");
        new Config<>(data, "config", RefixesConfig.get().getCodec()).load().join();
        require(DiagnosticsConfig.get().getValue(DiagnosticsConfig.JFR_EVENTS), "HyboxIntegration=false turned JFR events off");
        require(TickThrottled.CODEC.decode(new BsonDocument(), new ExtraInfo()) != null, "Saved ownership marker no longer decodes");

        Path isolated = Files.createTempDirectory("refixes-runtime-config-");
        Path mixinConfig = isolated.resolve("Refixes.json");
        String mixinJson = "{\"Mixins\":{\"Features\":{\"SharedInstances\":true,\"CustomPolicy\":true}}}";
        Files.writeString(mixinConfig, mixinJson);
        for (int restart = 0; restart < 3; restart++) {
            Config<RefixesConfig> nativeConfig = new Config<>(isolated, "config", RefixesConfig.get().getCodec());
            nativeConfig.load().join();
            nativeConfig.save().join();
            require(Files.readString(mixinConfig).equals(mixinJson), "Runtime config save changed early mixin settings");
            require(Files.isRegularFile(isolated.resolve("config.json")), "Native runtime config file was not saved");
        }
        System.out.println("ConfigMigrationRegression passed");
    }

    private static String load() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            Class<?> type = Class.forName(PLUGIN);
            type.getMethod("onLoad", String.class).invoke(type.getConstructor().newInstance(), "cc.irori.refixes.early.mixin");
        } finally {
            System.setOut(original);
        }
        String log = captured.toString(StandardCharsets.UTF_8);
        System.out.print(log.lines().filter(line -> !line.contains("[x]") && !line.contains("[ ]")).map(line -> line + "\n").reduce("", String::concat));
        return log;
    }

    private static JsonObject read(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void expect(JsonObject mixins, String path, boolean expected) {
        JsonElement current = mixins;
        for (String part : path.split("\\.")) current = current.getAsJsonObject().get(part);
        require(current != null && current.isJsonPrimitive(), "Missing toggle " + path);
        require(current.getAsBoolean() == expected, "Toggle " + path + " expected " + expected + " but was " + current);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
