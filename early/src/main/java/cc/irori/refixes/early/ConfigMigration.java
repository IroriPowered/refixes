package cc.irori.refixes.early;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigMigration {

    private record Rename(String from, boolean oldDefault, List<String> to) {}

    private record Move(String from, String to) {}

    private static final List<Rename> TOGGLE_RENAMES = List.of(
            rename("Optimizations.CollectVisible", true, "Tuning.CylinderVisibility"),
            rename("Optimizations.SkipSystemMetrics", false, "Diagnostics.SkipSystemMetrics"),
            rename("Optimizations.ChunkUnloadingSystem", true, "Features.OriginRetention"),
            rename("Optimizations.PlayerChunkTrackerSystems", true, "Tuning.ChunkStreaming"),
            rename("Experimental.ShutdownSaveTimeout", false, "Recovery.ShutdownSaveTimeout"),
            rename("Experimental.SkipEmptyLightSections", false, "Optimizations.SkipEmptyLightSections"),
            rename("Experimental.SharedInstances", false, "Features.SharedInstances"),
            rename("Experimental.TickSurvival", false, "Recovery.TickSurvival"),
            rename("Crashfixes.BlockSectionSafety", true, "Recovery.CorruptSectionReset"),
            rename("Crashfixes.MotionControllerBase", true, "Recovery.MotionControllerBase"),
            rename("Crashfixes.TurnOffTeleportersSystem", true, "Recovery.TurnOffTeleportersSystem"),
            rename("Crashfixes.EntityChunkLoadingSystem", true, "Recovery.InvalidSavedHolders"),
            rename("Crashfixes.CollisionModule", true, "Recovery.InvalidCollisionData"),
            rename("Crashfixes.PageManager", true, "Compatibility.PageManager"),
            rename("Crashfixes.TeleportToPlayerCommand", true, "Correctness.TeleportToPlayerCommand"),
            rename("Crashfixes.DeployableOwnerComponent", true, "Correctness.DeployableOwnerComponent"),
            rename("Crashfixes.MountPlugin", true, "Correctness.MountPlugin"),
            rename("Crashfixes.ChunkStoreSectionBackoffDeadlock", true, "Correctness.ChunkStoreSectionBackoffDeadlock"),
            rename("Crashfixes.DespawnSystem", true, "Correctness.DespawnSystem"),
            rename("Helpers.BlockComponentChunk", true, "Recovery.DuplicateComponentReferences"),
            rename("Helpers.CommandBuffer", true, "Correctness.CommandBuffer"),
            rename("Helpers.EntityViewer", true, "Correctness.EntityViewer"),
            rename("Helpers.HytaleServer", true, "Diagnostics.HytaleServer"),
            rename("Helpers.InteractionChain", true, "Recovery.InteractionChain"),
            rename("Helpers.MarkerAddRemoveSystem", true, "Correctness.MarkerAddRemoveSystem"),
            rename("Helpers.NPCKillsEntitySystem", true, "Correctness.NPCKillsEntitySystem"),
            rename("Helpers.PortalDeviceSummonPage", true, "Correctness.PortalDeviceSummonPage"),
            rename("Helpers.RemovalSystem", true, "Correctness.RemovalSystem"),
            rename("Helpers.ServerAuthManager", true, "Compatibility.ServerAuthManager"),
            rename("Helpers.SetMemoriesCapacityInteraction", true, "Correctness.SetMemoriesCapacityInteraction"),
            rename("Helpers.SpawnMarkerBlockStateHeartbeat", true, "Diagnostics.SpawnMarkerBlockStateHeartbeat"),
            rename("Helpers.StateSupport", true, "Correctness.StateSupport"),
            rename("Helpers.TickingSpawnMarkerSystem", true, "Correctness.TickingSpawnMarkerSystem"),
            rename("Helpers.TickSleep", true, "Tuning.TickWait"),
            rename("Helpers.TrackedPlacementOnAddRemove", true, "Correctness.TrackedPlacementOnAddRemove"),
            rename("Helpers.UUIDSystem", true, "Correctness.UUIDSystem"),
            rename("Helpers.UpdateCheckCommand", true, "Compatibility.UpdateCheckCommand"),
            rename("Helpers.UpdateDownloadCommand", true, "Compatibility.UpdateDownloadCommand"),
            rename("Helpers.UpdateModule", true, "Compatibility.UpdateModule"),
            rename("Helpers.WorldPauseCommand", true, "Features.WorldPauseCommand"),
            rename("Helpers.WorldConfig", true, "Compatibility.WorldConfig"),
            rename("Helpers.WorldSpawningSystem", true, "Recovery.WorldSpawningSystem"),
            new Rename("HypixelServices.LiveConfig", false, List.of("Mixins.Compatibility.DisableLiveConfig")),
            rename("Recovery.MarkerAddRemoveSystem", false, "Correctness.MarkerAddRemoveSystem"),
            rename("Recovery.TickingSpawnMarkerSystem", false, "Correctness.TickingSpawnMarkerSystem"),
            rename(
                    "Correctness.QuicheChannel",
                    true,
                    "Correctness.OutgoingQueueLimits",
                    "Correctness.SetupPacing",
                    "Correctness.QuicCloseRetention",
                    "Correctness.ChunkSectionFences"),
            rename(
                    "Correctness.WorldTransfer",
                    true,
                    "Correctness.WorldTransfer",
                    "Correctness.StorageOrdering",
                    "Correctness.TransferCombatClock"));

    private static final Map<String, String> RETIRED_TOGGLES = Map.ofEntries(
            Map.entry("Optimizations.FluidPlugin", "replaced by native fluid discovery in Hytale 0.8.0-pre.5"),
            Map.entry("Optimizations.BlockModule", "removed"),
            Map.entry("Optimizations.CollisionAirFastPath", "removed"),
            Map.entry("Optimizations.AStarBase", "limits moved to Mixins.Tuning.PathfindingLimits"),
            Map.entry("Optimizations.InteractionMetaSizing", "removed"),
            Map.entry("Optimizations.LegacySeparation", "removed"),
            Map.entry("Correctness.RespawnPointPage", "fixed in Hytale 0.7.7"),
            Map.entry("Helpers.WorldMapTracker", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Recovery.WorldMapTracker", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.WorldMapCache", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.WorldMapPacing", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.WorldMapTickingStop", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.WorldGenReload", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.WorldTaskQueueClose", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.ServerShutdownWatchdog", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.ServerManagerShutdown", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.ServerConfigSaveChain", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.UniverseDeletedCleanup", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.PacketAdapters", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.RocksDbFlush", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Optimizations.RocksDbCompression", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.RocksDbColumnRemoval", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.RocksDbScanBounds", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Optimizations.FluidDiscovery", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.FluidChangeSaving", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.FluidRelightSection", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.FluidDropSection", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.EntityModule", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.EntitySectionLoad", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.PrefabPasteLoaded", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.MapMarkerLocking", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.PortalDeviceWorldClose", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.WorldEventSaving", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.StoredFlockSaving", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.SerializableArchetype", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.TeleportInstanceInteraction", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.MountMovementTeleport", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.CommonAssetHashCleanup", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.ObjectiveEntityRemoval", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.SoundChannelEncoding", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.MessageSelect", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Optimizations.SoundHeaderValidation", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Experimental.Parallel.SpatialCollection", "parallel ticking removed"),
            Map.entry("Experimental.Parallel.AllSystems", "parallel ticking removed"),
            Map.entry("Experimental.Parallel.Steering", "parallel ticking removed"),
            Map.entry("Experimental.Parallel.RelaxStoreAsserts", "parallel ticking removed"),
            Map.entry("Experimental.Parallel.FluidReplicateChanges", "parallel ticking removed"),
            Map.entry("Experimental.Parallel.ChunkReplicateChanges", "parallel ticking removed"),
            Map.entry("Experimental.ConnectionBackpressure", "replaced by Mixins.Correctness.OutgoingQueueLimits"),
            Map.entry("Helpers.ArchetypeChunk", "removed"),
            Map.entry("Helpers.BeaconAddRemoveSystem", "removed"),
            Map.entry("Helpers.BlockHealthSystem", "removed"),
            Map.entry("Helpers.FluidSection", "now loaded by Runtime.Recovery.UnknownBlockCleaner.ScanFluids"),
            Map.entry("Helpers.GamePacketHandler", "removed"),
            Map.entry("Helpers.Options", "part of Mixins.Compatibility.ServerAuthManager"),
            Map.entry("Helpers.PlayerViewRadius", "part of Mixins.Tuning.ChunkStreaming"),
            Map.entry("Helpers.PortalWorldAccessor", "part of Mixins.Correctness.RemovalSystem"),
            Map.entry("Helpers.PrefabListExtraRoots", "removed"),
            Map.entry("Helpers.TickingThread", "removed"),
            Map.entry("Helpers.TickingThreadAssert", "removed"),
            Map.entry("Helpers.ChunkStoreAccess", "part of Mixins.Features.SharedInstanceStorage"),
            Map.entry("Helpers.TrackedPlacementAccessor", "part of Mixins.Correctness.TrackedPlacementOnAddRemove"),
            Map.entry(
                    "Helpers.World",
                    "split into Mixins.Correctness.WorldNullGuard, Mixins.Experimental.PathfindingBudget"
                            + " and Mixins.Recovery.ShutdownSaveTimeout"),
            Map.entry("Correctness.WorldRetirement", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.WorldReopenGuard", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.TickingThreadStop", "fixed in Hytale 0.8.0-pre.5"),
            Map.entry("Correctness.TickingThreadAbandon", "fixed in Hytale 0.8.0-pre.5"));

    private static final List<String> LEGACY_MIXIN_CATEGORIES = List.of("Mixins.Crashfixes", "Mixins.Helpers");

    private static final Map<String, Boolean> CHANGED_DEFAULTS = Map.of("Mixins.Optimizations.KDTree", true);

    private static final List<Move> RUNTIME_MOVES = List.of(
            new Move("HyboxIntegration", "Runtime.Diagnostics.HyboxIntegration"),
            new Move("BlackboxIntegration", "Runtime.Diagnostics.HyboxIntegration"),
            new Move("Listeners.UnknownBlockCleaner", "Runtime.Recovery.UnknownBlockCleaner.Enabled"),
            new Move("Listeners.UnknownBlockCleanerScanFluids", "Runtime.Recovery.UnknownBlockCleaner.ScanFluids"),
            new Move(
                    "Listeners.UnknownBlockCleanerScanContainers",
                    "Runtime.Recovery.UnknownBlockCleaner.ScanContainers"),
            new Move(
                    "Listeners.UnknownBlockCleanerScanPlayerInventories",
                    "Runtime.Recovery.UnknownBlockCleaner.ScanPlayerInventories"),
            new Move("Listeners.UnknownBlockCleanerExclude", "Runtime.Recovery.UnknownBlockCleaner.Exclude"),
            new Move("Listeners.UnknownBlockCleanerBudgetMs", "Runtime.Recovery.UnknownBlockCleaner.BudgetMs"),
            new Move("Listeners.UnknownBlockCleanerIntervalMs", "Runtime.Recovery.UnknownBlockCleaner.IntervalMs"),
            new Move("Systems.CraftingManager", "Runtime.Correctness.CraftingManager"),
            new Move("Systems.EntityDespawnTimerConfig", "Runtime.Tuning.EntityDespawnTimer"),
            new Move(
                    "Services.AiTickThrottler.CleanupExcludedNpcTypes",
                    "Runtime.Compatibility.TickThrottledCleanup.CleanupExcludedNpcTypes"),
            new Move(
                    "Services.AiTickThrottler.LegacyCleanup",
                    "Runtime.Compatibility.TickThrottledCleanup.LegacyCleanup"),
            new Move(
                    "Services.AiTickThrottler.LegacyCleanupExcludedNpcTypes",
                    "Runtime.Compatibility.TickThrottledCleanup.LegacyCleanupExcludedNpcTypes"),
            new Move("Services.PerPlayerHotRadius", "Runtime.Tuning.PerPlayerHotRadius"),
            new Move("Services.IdlePlayerHandler", "Runtime.Tuning.IdlePlayerHandler"),
            new Move("Services.AiTickThrottler", "Runtime.Tuning.AiTickThrottler"),
            new Move("Services.IdleWorldPause", "Runtime.Tuning.IdleWorldPause"),
            new Move("ChunkLoader.Enabled", "Runtime.Features.ChunkLoader.Enabled"),
            new Move("SharedInstanceWorlds", "Runtime.Features.SharedInstances"),
            new Move("Watchdog.Enabled", "Runtime.Diagnostics.Watchdog.Enabled"),
            new Move("Watchdog.DumpAllThreads", "Runtime.Diagnostics.Watchdog.DumpAllThreads"),
            new Move("Watchdog.ActivationDelayMs", "Runtime.Diagnostics.Watchdog.ActivationDelayMs"),
            new Move("Watchdog.ThreadTimeoutMs", "Runtime.Diagnostics.Watchdog.ThreadTimeoutMs"),
            new Move("Watchdog.ShutdownOnDefaultWorldCrash", "Runtime.Recovery.Watchdog.ShutdownOnDefaultWorldCrash"),
            new Move("Watchdog.AutoRestartWorlds", "Runtime.Recovery.Watchdog.AutoRestartWorlds"),
            new Move("Watchdog.ShutdownTimeoutMs", "Runtime.Recovery.Watchdog.ShutdownTimeoutMs"),
            new Move("Watchdog.RestartSaveTimeoutMs", "Runtime.Recovery.Watchdog.RestartSaveTimeoutMs"),
            new Move("Watchdog.AutoRestartingWorldFilter", "Runtime.Recovery.Watchdog.AutoRestartingWorldFilter"));

    private static final Map<String, String> RETIRED_RUNTIME = Map.of(
            "Systems.EntityDespawnTimer",
            "the despawn timer system now runs when one of its actions is enabled",
            "Early.BackpressureMaxOutboundBytes",
            "replaced by Early.OutgoingQueueMaxBytes",
            "Early.BackpressureGraceMs",
            "replaced by Early.OutgoingQueueMaxBytes and Early.OutgoingQueueMaxPackets",
            "Experimental.ParallelSteeringThreshold",
            "parallel ticking removed");

    private static final List<String> LEGACY_RUNTIME_ROOTS = List.of(
            "Listeners", "Systems", "Services", "ChunkLoader", "SharedInstanceWorlds", "Watchdog", "Experimental");

    private ConfigMigration() {}

    private static Rename rename(String from, boolean oldDefault, String... to) {
        List<String> targets = new ArrayList<>();
        for (String target : to) targets.add("Mixins." + target);
        return new Rename("Mixins." + from, oldDefault, List.copyOf(targets));
    }

    public static List<String> migrateMixins(JsonObject config, Set<String> currentToggles) {
        List<String> log = new ArrayList<>();
        if (LEGACY_MIXIN_CATEGORIES.stream().anyMatch(category -> get(config, category) != null)) {
            for (Map.Entry<String, Boolean> changed : CHANGED_DEFAULTS.entrySet()) {
                JsonElement value = get(config, changed.getKey());
                if (!isBoolean(value) || value.getAsBoolean() != changed.getValue()) continue;
                remove(config, changed.getKey());
                log.add("Removed " + changed.getKey() + "=" + value + " (old default); " + changed.getKey()
                        + " uses the current default");
            }
        }
        for (Rename rename : TOGGLE_RENAMES) {
            boolean split = rename.to.contains(rename.from);
            if (split && rename.to.stream().allMatch(target -> get(config, target) != null)) continue;
            JsonElement value = split ? get(config, rename.from) : remove(config, rename.from);
            if (value == null) continue;
            if (!isBoolean(value)) {
                log.add("Removed " + rename.from + ": value " + value + " is not true or false");
                continue;
            }
            boolean enabled = value.getAsBoolean();
            if (enabled == rename.oldDefault) {
                log.add((split ? "Split " : "Removed ") + rename.from + "=" + enabled + " (old default); "
                        + String.join(", ", rename.to) + " use the current default");
                continue;
            }
            List<String> migrated = new ArrayList<>();
            for (String target : rename.to) {
                if (target.equals(rename.from)) continue;
                JsonElement current = get(config, target);
                if (isBoolean(current)) {
                    log.add("Ignored " + rename.from + "=" + enabled + ": " + target + " is already set to " + current);
                } else {
                    set(config, target, new JsonPrimitive(enabled));
                    migrated.add(target);
                }
            }
            if (!migrated.isEmpty())
                log.add("Migrated " + rename.from + "=" + enabled + " to " + String.join(", ", migrated));
        }
        JsonElement services = config.get("HypixelServices");
        if (services != null
                && services.isJsonObject()
                && services.getAsJsonObject().isEmpty()) config.remove("HypixelServices");
        for (Map.Entry<String, String> retired : RETIRED_TOGGLES.entrySet()) {
            JsonElement value = remove(config, "Mixins." + retired.getKey());
            if (value != null)
                log.add("Removed retired setting Mixins." + retired.getKey() + "=" + value + " (" + retired.getValue()
                        + ")");
        }
        JsonElement mixins = config.get("Mixins");
        if (mixins != null && mixins.isJsonObject())
            reportUnknown(mixins.getAsJsonObject(), "Mixins", currentToggles, log);
        return log;
    }

    public static List<String> migrateRuntime(JsonObject config) {
        List<String> log = new ArrayList<>();
        JsonElement watchdog = get(config, "Watchdog.Enabled");
        boolean watchdogDisabled = isBoolean(watchdog) && !watchdog.getAsBoolean();
        JsonElement despawn = get(config, "Systems.EntityDespawnTimer");
        boolean despawnDisabled = isBoolean(despawn) && !despawn.getAsBoolean();
        JsonElement cleanup = remove(config, "Services.AiTickThrottler.CleanupFrozenEntities");
        if (cleanup != null) {
            String target = "Runtime.Compatibility.TickThrottledCleanup.CleanupFrozenEntities";
            if (isBoolean(cleanup) && !cleanup.getAsBoolean()) {
                log.add("Removed Services.AiTickThrottler.CleanupFrozenEntities=false (old default); " + target
                        + " uses the current default");
            } else {
                move(config, "Services.AiTickThrottler.CleanupFrozenEntities", target, cleanup, log);
            }
        }
        for (Move move : RUNTIME_MOVES) {
            JsonElement value = remove(config, move.from);
            if (value != null) move(config, move.from, move.to, value, log);
        }
        for (Map.Entry<String, String> retired : RETIRED_RUNTIME.entrySet()) {
            JsonElement value = remove(config, retired.getKey());
            if (value != null)
                log.add("Removed retired setting " + retired.getKey() + "=" + value + " (" + retired.getValue() + ")");
        }
        for (String root : LEGACY_RUNTIME_ROOTS) {
            JsonElement value = config.remove(root);
            if (value == null) continue;
            if (value.isJsonObject()) reportUnknown(value.getAsJsonObject(), root, Set.of(), log);
            else log.add("Removed unknown setting " + root + "=" + value);
        }
        if (despawnDisabled) {
            for (String action : List.of("AddTimerWhenMissing", "OverrideExistingTimers")) {
                set(config, "Runtime.Tuning.EntityDespawnTimer." + action, new JsonPrimitive(false));
            }
            log.add("Systems.EntityDespawnTimer=false: set Runtime.Tuning.EntityDespawnTimer.AddTimerWhenMissing and"
                    + " OverrideExistingTimers to false, so the despawn timer stays off as before");
        }
        if (watchdogDisabled) {
            for (String action : List.of("ShutdownOnDefaultWorldCrash", "AutoRestartWorlds")) {
                set(config, "Runtime.Recovery.Watchdog." + action, new JsonPrimitive(false));
            }
            log.add("Watchdog.Enabled=false: set Runtime.Recovery.Watchdog.ShutdownOnDefaultWorldCrash and"
                    + " AutoRestartWorlds to false, so the watchdog stays off as before");
        }
        return log;
    }

    private static void move(JsonObject config, String from, String to, JsonElement value, List<String> log) {
        if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet())
                move(config, from + "." + entry.getKey(), to + "." + entry.getKey(), entry.getValue(), log);
            return;
        }
        JsonElement current = get(config, to);
        if (current != null) {
            log.add("Ignored " + from + "=" + value + ": " + to + " is already set to " + current);
            return;
        }
        set(config, to, value);
        log.add("Migrated " + from + " to " + to);
    }

    private static void reportUnknown(JsonObject object, String path, Set<String> known, List<String> log) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String child = path + "." + entry.getKey();
            if (entry.getValue().isJsonObject()) {
                reportUnknown(entry.getValue().getAsJsonObject(), child, known, log);
            } else if (!known.contains(child)) {
                log.add("Removed unknown setting " + child + "=" + entry.getValue());
            } else if (!isBoolean(entry.getValue())) {
                log.add("Replaced invalid value " + child + "=" + entry.getValue() + " with the default");
            }
        }
    }

    private static boolean isBoolean(JsonElement value) {
        return value != null
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isBoolean();
    }

    private static JsonElement get(JsonObject root, String path) {
        JsonElement current = root;
        for (String part : path.split("\\.")) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current;
    }

    private static JsonElement remove(JsonObject root, String path) {
        int split = path.lastIndexOf('.');
        JsonElement parent = split < 0 ? root : get(root, path.substring(0, split));
        if (parent == null || !parent.isJsonObject()) return null;
        return parent.getAsJsonObject().remove(path.substring(split + 1));
    }

    private static void set(JsonObject root, String path, JsonElement value) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            JsonElement child = current.get(parts[i]);
            if (child == null || !child.isJsonObject()) {
                JsonObject created = new JsonObject();
                current.add(parts[i], created);
                current = created;
            } else current = child.getAsJsonObject();
        }
        current.add(parts[parts.length - 1], value);
    }
}
