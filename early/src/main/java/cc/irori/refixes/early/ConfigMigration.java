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
            rename("Optimizations.ChunkUnloadingSystem", true, "Features.OriginRetention"),
            rename("Optimizations.PlayerChunkTrackerSystems", true, "Tuning.ChunkStreaming"),
            rename("Experimental.ShutdownSaveTimeout", false, "Recovery.ShutdownSaveTimeout"),
            rename("Experimental.SharedInstances", false, "Features.SharedInstances"),
            rename("Experimental.TickSurvival", false, "Recovery.TickSurvival"),
            rename("Crashfixes.BlockSectionSafety", true, "Recovery.CorruptSectionReset"),
            rename("Crashfixes.MotionControllerBase", true, "Recovery.MotionControllerBase"),
            rename("Crashfixes.TurnOffTeleportersSystem", true, "Recovery.TurnOffTeleportersSystem"),
            rename("Crashfixes.CollisionModule", true, "Recovery.InvalidCollisionData"),
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
            rename("Helpers.StateSupport", true, "Correctness.StateSupport"),
            rename("Helpers.TickingSpawnMarkerSystem", true, "Correctness.TickingSpawnMarkerSystem"),
            rename("Helpers.TickSleep", true, "Tuning.TickWait"),
            rename("Helpers.TrackedPlacementOnAddRemove", true, "Correctness.TrackedPlacementOnAddRemove"),
            rename("Helpers.UpdateCheckCommand", true, "Compatibility.UpdateCheckCommand"),
            rename("Helpers.UpdateDownloadCommand", true, "Compatibility.UpdateDownloadCommand"),
            rename("Helpers.UpdateModule", true, "Compatibility.UpdateModule"),
            rename("Crashfixes.UpdateModule", true, "Compatibility.UpdateModule"),
            rename("Crashfixes.RepulsionTicker", true, "Optimizations.RepulsionTicker"),
            rename("Helpers.WorldPauseCommand", true, "Features.WorldPauseCommand"),
            rename("Helpers.WorldConfig", true, "Compatibility.WorldConfig"),
            rename("Helpers.WorldMapTracker", true, "Recovery.WorldMapTracker", "Correctness.WorldMapCache"),
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
                    "Correctness.ChunkSectionFences",
                    "Correctness.WorldMapPacing"),
            rename(
                    "Correctness.WorldTransfer",
                    true,
                    "Correctness.WorldTransfer",
                    "Correctness.WorldReopenGuard",
                    "Correctness.StorageOrdering",
                    "Correctness.TransferCombatClock"),
            rename(
                    "Correctness.LightSaveMarks",
                    true,
                    "Correctness.BlockChangeSaving",
                    "Optimizations.SkipUnchangedLightSaves"),
            rename("Optimizations.RepulsionNeighbours", false, "Optimizations.RepulsionNeighbors"));

    private static final Map<String, String> RETIRED_TOGGLES = Map.ofEntries(
            Map.entry("Optimizations.FluidPlugin", "no measurable gain"),
            Map.entry("Optimizations.BlockModule", "removed"),
            Map.entry("Optimizations.CollisionAirFastPath", "removed"),
            Map.entry("Optimizations.AStarBase", "limits moved to Mixins.Tuning.PathfindingLimits"),
            Map.entry("Optimizations.InteractionMetaSizing", "removed"),
            Map.entry("Optimizations.LegacySeparation", "removed"),
            Map.entry("Correctness.RespawnPointPage", "fixed in Hytale 0.7.7"),
            Map.entry("Optimizations.KDTree", "no measurable gain"),
            Map.entry("Optimizations.SkipSystemMetrics", "no measurable gain"),
            Map.entry("Diagnostics.SkipSystemMetrics", "no measurable gain"),
            Map.entry(
                    "Helpers.SetMemoriesCapacityInteraction",
                    "Hytale stops the server when the Memories plugin fails to start"),
            Map.entry(
                    "Correctness.SetMemoriesCapacityInteraction",
                    "Hytale stops the server when the Memories plugin fails to start"),
            Map.entry("Helpers.UUIDSystem", "Hytale cannot reach this case"),
            Map.entry("Correctness.UUIDSystem", "Hytale cannot reach this case"),
            Map.entry("Optimizations.FluidDiscovery", "no measurable gain"),
            Map.entry("Optimizations.CollisionConfig", "no measurable gain"),
            Map.entry("Experimental.SkipEmptyLightSections", "no measurable gain"),
            Map.entry("Optimizations.SkipEmptyLightSections", "no measurable gain"),
            Map.entry("Crashfixes.EntityChunkLoadingSystem", "Hytale drops these entities itself"),
            Map.entry("Recovery.InvalidSavedHolders", "Hytale drops these entities itself"),
            Map.entry("Helpers.SpawnMarkerBlockStateHeartbeat", "it only lowered the level of a Hytale error"),
            Map.entry("Diagnostics.SpawnMarkerBlockStateHeartbeat", "it only lowered the level of a Hytale error"),
            Map.entry("Crashfixes.PageManager", "Hytale catches and logs this error itself"),
            Map.entry("Compatibility.PageManager", "Hytale catches and logs this error itself"),
            Map.entry("Correctness.WorldNullGuard", "no Hytale code reaches this case"),
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
                    "split into Mixins.Experimental.PathfindingBudget and Mixins.Recovery.ShutdownSaveTimeout"),
            Map.entry("Crashfixes.AStarBase", "limits moved to Mixins.Tuning.PathfindingLimits"),
            Map.entry("Crashfixes.ChunkLightDataSerializeSafety", "removed"),
            Map.entry("Crashfixes.HideEntitySystems", "removed"),
            Map.entry("Crashfixes.Player", "removed"),
            Map.entry("Crashfixes.TriggerVolumesPlugin", "removed"),
            Map.entry("Crashfixes.VoiceModule", "removed"),
            Map.entry("Experimental.BlockSectionCache", "removed"),
            Map.entry("Experimental.StatRecalcThrottle", "removed"),
            Map.entry("Experimental.ParallelEntityTicking", "parallel ticking removed"),
            Map.entry("Experimental.ParallelSpatialCollection", "parallel ticking removed"),
            Map.entry("Experimental.FluidReplicateChanges", "parallel ticking removed"),
            Map.entry("Experimental.ChunkReplicateChanges", "parallel ticking removed"),
            Map.entry("Helpers.CraftingManagerAccessor", "removed"),
            Map.entry("Helpers.PrefabLoader", "removed"),
            Map.entry("Helpers.Universe", "removed"),
            Map.entry("Optimizations.ChunkSavingSystems", "removed"));

    private static final List<Move> RUNTIME_MOVES = List.of(
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

    private static final String JFR_INSTEAD =
            "Refixes records JDK Flight Recorder events instead, controlled by Runtime.Diagnostics.JfrEvents.";

    private static final Map<String, String> RETIRED_RUNTIME = Map.ofEntries(
            Map.entry(
                    "Systems.EntityDespawnTimer",
                    "the despawn timer system now runs when one of its actions is enabled"),
            Map.entry("Early.BackpressureMaxOutboundBytes", "replaced by Early.OutgoingQueueMaxBytes"),
            Map.entry(
                    "Early.BackpressureGraceMs",
                    "replaced by Early.OutgoingQueueMaxBytes and Early.OutgoingQueueMaxPackets"),
            Map.entry("Experimental.ParallelSteeringThreshold", "parallel ticking removed"),
            Map.entry("Early.KDTreeOptimization", "no measurable gain"),
            Map.entry("Runtime.Diagnostics.HyboxIntegration", JFR_INSTEAD),
            Map.entry("HyboxIntegration", JFR_INSTEAD),
            Map.entry("BlackboxIntegration", JFR_INSTEAD),
            Map.entry("Early.MaxChunksPerSecond", "replaced by Early.MaxSectionsPerSecond"),
            Map.entry("Early.MaxChunksPerTick", "replaced by Early.MaxSectionsPerTick"),
            Map.entry("Early.TickSleepOptimization", "replaced by Mixins.Tuning.TickWait"),
            Map.entry("Early.ForceSkipModValidation", "removed"),
            Map.entry("Early.StatRecalcInterval", "removed"),
            Map.entry("Listeners.InstancePositionTracker", "removed"),
            Map.entry("Systems.RespawnBlock", "removed"),
            Map.entry("Services.ChunkUnloader", "removed"),
            Map.entry(
                    "Services.ConnectionBackpressure",
                    "replaced by Early.OutgoingQueueMaxBytes and Early.OutgoingQueueMaxPackets"),
            Map.entry("Services.PerPlayerHotRadius.TPSLow", "replaced by TPSLowFraction"),
            Map.entry("Services.PerPlayerHotRadius.TPSHigh", "replaced by TPSHighFraction"),
            Map.entry("Services.PerPlayerHotRadius.AdjustmentStep", "removed"));

    private static final List<String> LEGACY_RUNTIME_ROOTS = List.of(
            "Listeners", "Systems", "Services", "ChunkLoader", "SharedInstanceWorlds", "Watchdog", "Experimental");

    private static final String REOPEN_GUARD = "Mixins.Correctness.WorldReopenGuard";
    private static final String RETIREMENT = "Mixins.Correctness.WorldRetirement";

    private ConfigMigration() {}

    private static Rename rename(String from, boolean oldDefault, String... to) {
        List<String> targets = new ArrayList<>();
        for (String target : to) targets.add("Mixins." + target);
        return new Rename("Mixins." + from, oldDefault, List.copyOf(targets));
    }

    private static final String ALLOW_UNSUPPORTED_SERVER_VERSION = "AllowUnsupportedServerVersion";
    private static final String UNCHANGED_PATCHES = "ApplyUnchangedPatchesOnUnsupportedServerVersion";

    public static List<String> migrateOtherServerVersions(JsonObject config) {
        String key = RefixesMixinPlugin.OTHER_SERVER_VERSIONS;
        List<String> log = new ArrayList<>();
        JsonElement allow = config.remove(ALLOW_UNSUPPORTED_SERVER_VERSION);
        JsonElement unchanged = config.remove(UNCHANGED_PATCHES);
        String current = otherServerVersions(config.get(key));
        boolean allPatches = isBoolean(allow) && allow.getAsBoolean();
        if (allow != null) {
            if (allPatches) {
                log.add(carryOver(
                        config, ALLOW_UNSUPPORTED_SERVER_VERSION, allow, current, RefixesMixinPlugin.ALL_PATCHES));
            } else if (isBoolean(allow)) {
                log.add("Removed " + ALLOW_UNSUPPORTED_SERVER_VERSION + "=false (old default)");
            } else {
                log.add("Removed invalid value " + ALLOW_UNSUPPORTED_SERVER_VERSION + "=" + allow);
            }
        }
        if (unchanged != null) {
            String value = isBoolean(unchanged)
                    ? (unchanged.getAsBoolean() ? null : RefixesMixinPlugin.NEVER)
                    : otherServerVersions(unchanged);
            if (allPatches) {
                log.add("Removed " + UNCHANGED_PATCHES + "=" + unchanged + ", " + ALLOW_UNSUPPORTED_SERVER_VERSION
                        + "=true takes precedence");
            } else if (isBoolean(unchanged) && unchanged.getAsBoolean()) {
                log.add("Removed " + UNCHANGED_PATCHES + "=true (old default), " + key + " uses the default "
                        + RefixesMixinPlugin.OTHER_SERVER_VERSIONS_DEFAULT);
            } else if (value == null) {
                log.add("Removed invalid value " + UNCHANGED_PATCHES + "=" + unchanged);
            } else {
                log.add(carryOver(config, UNCHANGED_PATCHES, unchanged, current, value));
            }
        }
        JsonElement value = config.get(key);
        if (value == null) return log;
        String accepted = otherServerVersions(value);
        if (accepted == null) {
            config.remove(key);
            log.add("Replaced invalid value " + key + "=" + value + " with the default");
        } else {
            config.addProperty(key, accepted);
        }
        return log;
    }

    private static String carryOver(JsonObject config, String from, JsonElement old, String current, String value) {
        String key = RefixesMixinPlugin.OTHER_SERVER_VERSIONS;
        if (current != null) return "Ignored " + from + "=" + old + ": " + key + " is already set to " + current;
        config.addProperty(key, value);
        return "Migrated " + from + "=" + old + " to " + key + "=" + value;
    }

    private static String otherServerVersions(JsonElement value) {
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) return null;
        for (String accepted : RefixesMixinPlugin.OTHER_SERVER_VERSIONS_VALUES) {
            if (accepted.equalsIgnoreCase(value.getAsString().trim())) return accepted;
        }
        return null;
    }

    public static List<String> migrateMixins(JsonObject config, Set<String> currentToggles) {
        List<String> log = new ArrayList<>();
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
                if (target.equals(REOPEN_GUARD) && Boolean.TRUE.equals(booleanAt(config, RETIREMENT))) {
                    log.add("Kept " + REOPEN_GUARD + " on: " + RETIREMENT + " needs it");
                    continue;
                }
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
        for (Map.Entry<String, String> retired : RETIRED_RUNTIME.entrySet()) {
            JsonElement value = remove(config, retired.getKey());
            if (value != null)
                log.add("Removed retired setting " + retired.getKey() + "=" + value + " (" + retired.getValue() + ")");
        }
        for (Move move : RUNTIME_MOVES) {
            JsonElement value = remove(config, move.from);
            if (value != null) move(config, move.from, move.to, value, log);
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

    private static Boolean booleanAt(JsonObject config, String path) {
        JsonElement value = get(config, path);
        return isBoolean(value) ? value.getAsBoolean() : null;
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
