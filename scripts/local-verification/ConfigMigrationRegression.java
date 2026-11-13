package cc.irori.refixes.config.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.util.Config;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigMigrationRegression {
    private static final String PLUGIN = "cc.irori.refixes.early.RefixesMixinPlugin";

    public static void main(String[] args) throws Exception {
        Path data = Path.of("mods/IroriPowered_Refixes");
        Files.createDirectories(data);
        Files.writeString(data.resolve("Refixes.json"), """
                {
                  "Mixins": {
                    "Optimizations": {"CollectVisible": true, "KDTree": true, "FluidPlugin": true, "AStarBase": false},
                    "Experimental": {"ShutdownSaveTimeout": true, "TickSurvival": false, "Parallel": {"CollectVisible": false, "AllSystems": true}},
                    "Crashfixes": {"BlockSectionSafety": true, "CollisionModule": false, "MountPlugin": false},
                    "Helpers": {"WorldMapTracker": true, "TickSleep": false, "World": false, "MarkerAddRemoveSystem": false},
                    "Correctness": {"QuicheChannel": false, "WorldTransfer": false}
                  },
                  "HypixelServices": {"LiveConfig": true}
                }
                """);
        Files.writeString(data.resolve("config.json"), """
                {
                  "BlackboxIntegration": false,
                  "Early": {"MaxSectionsPerTick": 12, "BackpressureMaxOutboundBytes": 1024},
                  "Listeners": {"UnknownBlockCleaner": true, "UnknownBlockCleanerScanFluids": true, "UnknownBlockCleanerBudgetMs": 7},
                  "Systems": {"CraftingManager": false, "EntityDespawnTimer": false, "EntityDespawnTimerConfig": {"ItemDespawnSeconds": 99, "AddTimerWhenMissing": true}},
                  "Services": {
                    "PerPlayerHotRadius": {"Enabled": false, "MaxRadius": 6},
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
        expect(mixins, "Optimizations.KDTree", false);
        expect(mixins, "Experimental.Parallel.CollectVisible", false);
        require(!mixins.has("Crashfixes") && !mixins.has("Helpers"), "Retired categories remain");
        require(!mixins.getAsJsonObject("Experimental").getAsJsonObject("Parallel").has("AllSystems"), "Retired toggle remains");
        require(!mixins.getAsJsonObject("Correctness").has("QuicheChannel"), "Split toggle remains");
        require(!read(data.resolve("Refixes.json")).has("HypixelServices"), "Migrated legacy root remains");
        for (String line : new String[] {
            "Removed Mixins.Optimizations.KDTree=true (old default)",
            "Migrated Mixins.Experimental.ShutdownSaveTimeout=true to Mixins.Recovery.ShutdownSaveTimeout",
            "Removed Mixins.Crashfixes.BlockSectionSafety=true (old default)",
            "Removed retired setting Mixins.Optimizations.FluidPlugin=true",
            "Removed retired setting Mixins.Experimental.Parallel.AllSystems=true",
            "Removed retired setting Mixins.Helpers.World=false",
            "Removed retired setting Mixins.Helpers.WorldMapTracker=true (fixed in Hytale 0.8.0-pre.5)",
            "Migrated Mixins.Correctness.QuicheChannel=false to Mixins.Correctness.OutgoingQueueLimits",
            "Removed retired setting Early.BackpressureMaxOutboundBytes=1024",
            "Removed retired setting Experimental.ParallelSteeringThreshold=32",
            "Migrated Services.AiTickThrottler.LegacyCleanup to Runtime.Compatibility.TickThrottledCleanup.LegacyCleanup",
            "Removed Services.AiTickThrottler.CleanupFrozenEntities=false (old default)",
            "Watchdog.Enabled=false"
        }) require(log.contains(line), "Missing migration log line: " + line + "\n" + log);

        JsonObject runtimeJson = read(data.resolve("config.json"));
        for (String root : new String[] {"BlackboxIntegration", "Listeners", "Systems", "Services", "ChunkLoader", "SharedInstanceWorlds", "Watchdog", "Experimental"})
            require(!runtimeJson.has(root), "Legacy root section remains: " + root);
        require(!runtimeJson.getAsJsonObject("Early").has("BackpressureMaxOutboundBytes"), "Retired early key remains");

        Config<RefixesConfig> config = new Config<>(data, "config", RefixesConfig.get().getCodec());
        config.load().join();
        require(!DiagnosticsConfig.get().getValue(DiagnosticsConfig.HYBOX_INTEGRATION), "BlackboxIntegration not migrated");
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

        JsonObject current = read(data.resolve("Refixes.json"));
        current.getAsJsonObject("Mixins").getAsJsonObject("Optimizations").addProperty("KDTree", true);
        Files.writeString(data.resolve("Refixes.json"), current.toString());
        String enabled = load();
        require(!enabled.contains("Removed"), "Current-format KDTree=true was treated as an old default:\n" + enabled);
        expect(read(data.resolve("Refixes.json")).getAsJsonObject("Mixins"), "Optimizations.KDTree", true);

        Files.writeString(data.resolve("Refixes.json"), """
                {"Mixins": {"Optimizations": {"KDTree": false}, "Helpers": {"TickSleep": true}}}
                """);
        String explicit = load();
        require(!explicit.contains("Optimizations.KDTree"), "Explicit 0.5.0 KDTree=false was migrated:\n" + explicit);
        expect(read(data.resolve("Refixes.json")).getAsJsonObject("Mixins"), "Optimizations.KDTree", false);

        Files.writeString(data.resolve("config.json"), """
                {"HyboxIntegration": false}
                """);
        String hybox = load();
        require(hybox.contains("Migrated HyboxIntegration to Runtime.Diagnostics.HyboxIntegration"), "HyboxIntegration not migrated:\n" + hybox);
        JsonObject hyboxJson = read(data.resolve("config.json"));
        require(!hyboxJson.has("HyboxIntegration"), "Legacy HyboxIntegration root remains");
        expect(hyboxJson.getAsJsonObject("Runtime"), "Diagnostics.HyboxIntegration", false);
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
