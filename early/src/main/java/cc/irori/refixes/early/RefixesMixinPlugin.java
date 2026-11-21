package cc.irori.refixes.early;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.net.JarURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.service.ServiceNotAvailableError;

public class RefixesMixinPlugin implements IMixinConfigPlugin {

    private static final Path CONFIG_PATH = Paths.get("mods", "IroriPowered_Refixes", "Refixes.json");
    private static final Path RUNTIME_CONFIG_PATH = CONFIG_PATH.resolveSibling("config.json");
    static final String OTHER_SERVER_VERSIONS = "OtherServerVersions";
    static final String NEVER = "Never";
    static final String SAME_MINOR_VERSION = "SameMinorVersion";
    static final String ALL_NEWER_VERSIONS = "AllNewerVersions";
    static final String ALL_PATCHES = "AllPatches";
    static final String OTHER_SERVER_VERSIONS_DEFAULT = SAME_MINOR_VERSION;
    static final List<String> OTHER_SERVER_VERSIONS_VALUES =
            List.of(NEVER, SAME_MINOR_VERSION, ALL_NEWER_VERSIONS, ALL_PATCHES);
    private static final Set<String> SAME_VERSION_CATEGORIES = Set.of("Recovery");
    private static final String SAME_VERSION_REASON = "Recovery switches change how Hytale handles failures, and Hytale"
            + " changes that handling between versions, so they apply only on the version Refixes was built for";

    private record WorldData(boolean destructive, String reason) {}

    private static WorldData additive(String reason) {
        return new WorldData(false, reason);
    }

    private static WorldData destructive(String reason) {
        return new WorldData(true, reason);
    }

    private static final Map<String, WorldData> WORLD_DATA_SWITCHES = Map.ofEntries(
            Map.entry(
                    "Mixins.Correctness.StorageOrdering",
                    destructive("defers deleting removed world folders and can stop waiting for storage to close at"
                            + " shutdown")),
            Map.entry(
                    "Mixins.Correctness.ChunkSaveQueueOrder",
                    additive("only puts requeued chunks at the back of the save queue")),
            Map.entry(
                    "Mixins.Correctness.BlockChangeSaving",
                    additive("only marks block sections dirty when their blocks change")),
            Map.entry(
                    "Mixins.Optimizations.SkipUnchangedLightSaves",
                    destructive("skips section saves the light pass would have queued")),
            Map.entry(
                    "Mixins.Correctness.ChunkStoreSectionBackoffDeadlock",
                    additive("only avoids a lock deadlock when a chunk load fails")),
            Map.entry(
                    "Mixins.Features.SharedInstanceStorage",
                    destructive("replaces the chunk saver so each shared instance chunk is written once and later"
                            + " saves and removals are dropped")),
            Map.entry(
                    "Mixins.Features.SharedInstances",
                    destructive("resets shared instance worlds by deleting their saved folders")),
            Map.entry(
                    "Mixins.Correctness.RemovalSystem",
                    destructive("starts the saved portal timer of shared instance worlds and changes when they are"
                            + " removed")),
            Map.entry(
                    "Mixins.Features.OriginRetention",
                    additive("only keeps the origin chunk and its sections loaded so they are never unloaded early")),
            Map.entry(
                    "Mixins.Tuning.WorldMapExistingTerrain",
                    additive("only reads chunks that already exist for the world map and never generates or rewrites"
                            + " them")),
            Map.entry(
                    "Mixins.Correctness.WorldTransfer",
                    destructive("moves players between world saves and resets their ownership")),
            Map.entry(
                    "Mixins.Correctness.WorldOwnership",
                    additive("only makes each world register and stop once by its owner")),
            Map.entry(
                    "Mixins.Compatibility.AiTickThrottler",
                    destructive("strips Frozen components from the NPC data it writes")),
            Map.entry(
                    "Mixins.Optimizations.CodecMetadata",
                    destructive("changes codec metadata handling while data is saved and loaded")));
    private static final Map<String, String> RUNTIME_RECOVERY_SWITCHES = Map.of(
            "Mixins.Compatibility.UnknownBlockCleaner", "lets the runtime cleaner rewrite stored blocks",
            "Mixins.Compatibility.Watchdog", "lets the watchdog save, remove and reload crashed worlds");
    private static final List<String> CATEGORIES = List.of(
            "Correctness",
            "Optimizations",
            "Tuning",
            "Features",
            "Recovery",
            "Compatibility",
            "Diagnostics",
            "Experimental");

    private static final String TARGET_SERVER_VERSION = "0.8.0-pre.6";

    private record MixinToggle(
            String[] jsonPath,
            boolean defaultEnabled,
            List<String> mixins,
            boolean runtimeDependency,
            List<String> uses) {}

    private record Dependency(String feature, String dependency) {}

    private static final List<Dependency> DEPENDENCIES = List.of(
            new Dependency("Mixins.Experimental.Parallel.CollectVisible", "Mixins.Correctness.ParallelTaskLifecycle"),
            new Dependency("Mixins.Correctness.OutgoingQueueLimits", "Mixins.Correctness.SetupPacing"),
            new Dependency("Mixins.Features.SharedInstances", "Mixins.Features.SharedInstanceStorage"),
            new Dependency("Mixins.Optimizations.SkipUnchangedLightSaves", "Mixins.Correctness.BlockChangeSaving"));

    private static final List<MixinToggle> TOGGLES = List.of(
            toggle("Optimizations", "SpawnManagerRecalc", false, "MixinWorldSpawnManager", "MixinWorldSpawnLifecycle"),
            toggle("Optimizations", "RepulsionTicker", false, "MixinRepulsionTicker"),
            toggle("Optimizations", "RepulsionNeighbors", false, "MixinRepulsionNeighbors"),
            toggle("Optimizations", "SkipEmptyLightSections", false, "MixinFloodLightCalculation"),
            toggle(
                    "Optimizations",
                    "LightNeutralBlocks",
                    false,
                    "MixinBlockSectionLightNeutral",
                    "MixinFloodLightNeutral",
                    "MixinFluidSectionLightNeutral",
                    "MixinBlockTypeAssetMapLightGeneration"),
            toggle(
                    "Optimizations",
                    "SkipUnchangedLightSaves",
                    false,
                    "MixinFloodLightSaveMarks",
                    "MixinBlockSectionStoredLight",
                    "MixinChunkLightDataAccess"),
            toggle(
                    "Optimizations",
                    "AssetPartSnapshotSharing",
                    false,
                    "MixinPacketHandlerAssetParts",
                    "MixinCachedPacketAccess"),
            toggle("Optimizations", "DefaultMovementResistance", false, "MixinDefaultMovementResistance"),
            toggle("Optimizations", "CodecMetadata", true, "MixinCodecMetadata"),
            toggle("Diagnostics", "HytaleServer", true, "MixinHytaleServer"),
            toggle("Diagnostics", "PatchCounters", false),
            toggle("Tuning", "CylinderVisibility", false, "MixinCollectVisible"),
            toggle("Tuning", "ChunkStreaming", false, "MixinPlayerChunkTrackerSystems", "MixinPlayerViewRadius"),
            toggle(
                    "Tuning",
                    "WorldMapExistingTerrain",
                    false,
                    "MixinImageBuilderExistingTerrain",
                    "MixinWorldMapTrackerMissingTerrain",
                    "MixinWorldMapImageTrackerMissingTerrain",
                    "MixinChunkGridExistingTerrain"),
            toggle("Tuning", "TickWait", true, "MixinTickSleep"),
            toggle("Tuning", "PathfindingLimits", false, "MixinAStarLimits"),
            toggle("Features", "OriginRetention", false, "MixinChunkUnloadingSystem", "MixinSectionUnloadingSystem"),
            toggle(
                    "Features",
                    "SharedInstances",
                    false,
                    "MixinInstancesPlugin",
                    "MixinSharedInstanceResetWorld",
                    "MixinSharedInstanceResetRemoval",
                    "MixinSharedInstanceResetUniverse"),
            toggle(
                    "Features",
                    "SharedInstanceStorage",
                    true,
                    "MixinChunkGridAccess",
                    "MixinChunkStoreAccess",
                    "MixinSharedInstanceWorld",
                    "MixinSharedInstanceStartup",
                    "MixinSharedInstanceRemoval"),
            toggle("Features", "WorldPauseCommand", false, "MixinWorldPauseCommand"),
            toggle("Recovery", "ShutdownSaveTimeout", false, "MixinStoreShutdown", "MixinWorldShutdown"),
            toggle("Recovery", "TickSurvival", false, "MixinStoreTickGuard", "MixinCommandBufferAccess"),
            toggle("Recovery", "CorruptSectionReset", false, "MixinBlockSectionSafety", "MixinBlockSectionQuarantine"),
            toggle("Recovery", "MotionControllerBase", false, "MixinMotionControllerBase"),
            toggle("Recovery", "TurnOffTeleportersSystem", false, "MixinTurnOffTeleportersSystem"),
            toggle(
                    "Recovery",
                    "UnresolvedNPCRoles",
                    false,
                    "MixinNPCSectionActivation",
                    "MixinNPCRoleActivation",
                    "MixinUnresolvedNPCRoleChange",
                    "MixinRoleChangeRequestAccess",
                    "MixinRoleChangeFlagAccess",
                    "MixinBuilderDependencyAccess",
                    "MixinBuilderValidationReferences"),
            toggle("Recovery", "InvalidCollisionData", false, "MixinCollisionModule"),
            toggle("Correctness", "MountPlugin", true, "MixinMountPlugin"),
            toggle(
                    "Correctness",
                    "OutgoingQueueLimits",
                    true,
                    "MixinQuicheChannel",
                    "MixinQuicheConnection",
                    "MixinNettyChannelConnection",
                    "MixinCommonAssetBroadcast",
                    "MixinCommonAssetMonitorBroadcast",
                    "MixinQuicStreamProgress"),
            toggle(
                    "Correctness",
                    "SetupPacing",
                    true,
                    "MixinSetupAssetPacing",
                    "MixinSetupStagePacing",
                    "MixinSetupConfigPacing"),
            toggle(
                    "Correctness",
                    "QuicCloseRetention",
                    true,
                    "MixinQuicChannelClose",
                    "MixinQuicConnectionAccess",
                    "MixinQuicTimeoutAccess"),
            toggle(
                    "Correctness",
                    "ChunkSectionFences",
                    true,
                    "MixinChunkTrackerSectionFence",
                    "MixinChunkTrackerQueryReentry",
                    "MixinReplicateSnapshotFence",
                    "MixinFluidReplicateLoadFence",
                    "MixinFluidReplicateSnapshotFence",
                    "MixinTrackedColumnSectionFence",
                    "MixinChunkTrackerNotificationResync",
                    "MixinBlockChunkNotificationPacket",
                    "MixinEnvironmentNotificationPacket",
                    "MixinWorldNotificationHandler",
                    "MixinPacketHandlerSectionFence"),
            toggle("Recovery", "InteractionChain", false, "MixinInteractionChain"),
            toggle("Correctness", "MarkerAddRemoveSystem", true, "MixinMarkerAddRemoveSystem"),
            toggle("Correctness", "TickingSpawnMarkerSystem", true, "MixinTickingSpawnMarkerSystem"),
            toggle("Recovery", "WorldSpawningSystem", false, "MixinWorldSpawningSystem"),
            toggle("Recovery", "DuplicateComponentReferences", false, "MixinBlockComponentChunk"),
            toggle("Correctness", "TrackedPlacementOnAddRemove", true, "MixinTrackedPlacementOnAddRemove"),
            toggle("Compatibility", "DisableLiveConfig", false, "MixinLiveConfigModule"),
            toggle("Compatibility", "ServerAuthManager", true, "MixinServerAuthManager", "MixinOptions"),
            toggle("Compatibility", "UpdateCheckCommand", true, "MixinUpdateCheckCommand"),
            toggle("Compatibility", "UpdateDownloadCommand", true, "MixinUpdateDownloadCommand"),
            toggle("Compatibility", "UpdateModule", true, "MixinUpdateModule"),
            toggle("Compatibility", "WorldConfig", true, "MixinWorldConfig"),
            runtimeToggle("PlayerRadius", "MixinChunkTrackerRadiusWrites", "MixinPlayerRadiusWrites"),
            runtimeToggle("ServerViewRadius", "MixinServerViewRadius"),
            runtimeToggle(
                    "AiTickThrottler",
                    "MixinThrottleFreezeSave",
                    "MixinFrozenOwnership",
                    "MixinMotionControllerAccess"),
            runtimeToggle("IdleWorldPause", "MixinWorldPauseOwner"),
            runtimeToggle("UnknownBlockCleaner", "MixinBlockComponentSectionAccess", "MixinFluidSection"),
            runtimeToggle("Watchdog", "MixinTickingThreadAccess"),
            toggle("Correctness", "WindowManager", true, "MixinWindowManager", "MixinWindowAccess"),
            toggle(
                    "Correctness",
                    "ConfigLoad",
                    true,
                    "MixinConfig",
                    "MixinPluginConfigWrites",
                    "MixinPluginManagerConfigWrites"),
            toggle("Correctness", "PluginPreload", true, "MixinPluginManagerPreload", "MixinPluginBaseShutdownAccess"),
            toggle("Correctness", "PluginLoaderCache", true, "MixinPluginManagerLoaderCache"),
            toggle(
                    "Correctness",
                    "WorldTransfer",
                    true,
                    "MixinWorldTransfer",
                    "MixinPlayerTeleport",
                    "MixinEntityTransferAdmission",
                    "MixinUniverseResetOwnership",
                    "MixinPlayerResetCommand"),
            toggle(
                    "Correctness",
                    "StorageOrdering",
                    true,
                    "MixinSaveCompletionWorld",
                    "MixinSaveCompletionShutdown",
                    "MixinSaveCompletionTicking",
                    "MixinSaveCompletionChunk",
                    "MixinSaveCompletionSection",
                    "MixinSaveCompletionEntity",
                    "MixinSaveCompletionRocksDb",
                    "MixinIndexedColumnSaveOrder",
                    "MixinIndexedCacheLifetime",
                    "MixinIndexedLoaderLifetime",
                    "MixinIndexedProviderLifetime",
                    "MixinWorldTransferCleanup",
                    "MixinRocksDbColumnSaveOrder"),
            toggle("Correctness", "ChunkSaveQueueOrder", true, "MixinChunkSaveQueueOrder", "MixinChunkSaveQueueData"),
            toggle(
                    "Correctness",
                    "BlockChangeSaving",
                    true,
                    "MixinBlockSectionSaveMarks",
                    "MixinChunkGridSectionSaveOwner",
                    "MixinFloodLightSaveOwner"),
            toggle("Correctness", "TransferCombatClock", true, "MixinTransferCombatClock"),
            toggle(
                    "Correctness",
                    "ParallelTaskLifecycle",
                    true,
                    "MixinParallelTask",
                    "MixinParallelRangeTask",
                    "MixinParallelRangeTask$SubTask",
                    "MixinParallelTaskData"),
            toggle(
                    "Correctness",
                    "ChunkStoreSectionBackoffDeadlock",
                    true,
                    "MixinChunkStoreSectionBackoffDeadlock",
                    "MixinChunkStoreLoadStateFail"),
            toggle("Correctness", "DespawnSystem", true, "MixinDespawnSystem"),
            toggle("Correctness", "CommandBuffer", true, "MixinCommandBuffer"),
            toggle("Correctness", "EntityViewer", true, "MixinEntityViewer"),
            toggle("Correctness", "EffectConditionClock", true, "MixinLivingEntityEffectSystem"),
            toggle("Correctness", "AssetInheritance", true, "MixinAssetStoreInheritance"),
            toggle("Correctness", "NPCKillsEntitySystem", true, "MixinNPCKillsEntitySystem"),
            toggle("Correctness", "PortalDeviceSummonPage", true, "MixinPortalDeviceSummonPage"),
            toggle("Correctness", "RemovalSystem", true, "MixinRemovalSystem", "MixinPortalWorldAccessor"),
            toggle("Correctness", "StateSupport", true, "MixinStateSupport"),
            toggle("Correctness", "DeployableOwnerComponent", true, "MixinDeployableOwnerComponent"),
            toggle("Correctness", "TeleportToPlayerCommand", true, "MixinTeleportToPlayerCommand"),
            using(
                    toggle("Correctness", "TickingThreadStart", true, "MixinTickingThreadStart"),
                    "MixinTickingThreadRunAccess"),
            using(
                    toggle(
                            "Correctness",
                            "WorldOwnership",
                            true,
                            "MixinUniverseWorldOwnership",
                            "MixinWorldOwnership",
                            "MixinPortalInvalidDestinationSystem"),
                    "MixinTickingThreadAccess"),
            toggle("Experimental", "Parallel", "CollectVisible", false, "MixinCollectVisibleParallel"),
            toggle(
                    "Experimental",
                    "PathfindingBudget",
                    false,
                    "MixinBodyMotionFindBase",
                    "MixinWorldPathfindingBudget"));

    private static final Set<String> ALL_MIXINS = allMixins();

    private String mixinPackage;
    private volatile Set<String> enabledMixins = Collections.emptySet();
    private volatile boolean initialized;

    private static MixinToggle toggle(String category, String key, boolean defaultEnabled, String... mixins) {
        return new MixinToggle(
                new String[] {"Mixins", category, key}, defaultEnabled, List.of(mixins), false, List.of());
    }

    private static MixinToggle toggle(
            String category, String group, String key, boolean defaultEnabled, String... mixins) {
        return new MixinToggle(
                new String[] {"Mixins", category, group, key}, defaultEnabled, List.of(mixins), false, List.of());
    }

    private static MixinToggle runtimeToggle(String key, String... mixins) {
        return new MixinToggle(new String[] {"Mixins", "Compatibility", key}, true, List.of(mixins), true, List.of());
    }

    private static MixinToggle using(MixinToggle toggle, String... accessors) {
        return new MixinToggle(
                toggle.jsonPath, toggle.defaultEnabled, toggle.mixins, toggle.runtimeDependency, List.of(accessors));
    }

    private static Set<String> allMixins() {
        Set<String> mixins = new HashSet<>();
        for (MixinToggle toggle : TOGGLES) {
            mixins.addAll(toggle.mixins);
            mixins.addAll(toggle.uses);
        }
        return Set.copyOf(mixins);
    }

    private static String path(MixinToggle toggle) {
        return String.join(".", toggle.jsonPath);
    }

    @Override
    public void onLoad(String mixinPackage) {
        initialized = false;
        enabledMixins = Collections.emptySet();
        EarlyOptions.clearMixinSelection();
        this.mixinPackage = mixinPackage;
        try {
            load();
        } catch (IllegalStateException failure) {
            throw new MixinInitialisationError("[Refixes] " + failure.getMessage(), failure);
        }
    }

    private void load() {
        String serverVersion = readServerVersion();
        JsonObject config = readConfig(CONFIG_PATH);
        List<String> settingMigrated = ConfigMigration.migrateOtherServerVersions(config);
        ServerVersion.Relation relation = ServerVersion.relation(TARGET_SERVER_VERSION, serverVersion);
        String versionLabel = serverVersion == null ? "unknown" : serverVersion;
        boolean unsupportedServerVersion = relation != ServerVersion.Relation.TARGET;
        String otherServerVersions = otherServerVersions(config);
        boolean overridden = unsupportedServerVersion && otherServerVersions.equals(ALL_PATCHES);
        boolean unchangedOnly = !overridden
                && switch (relation) {
                    case NEWER_SAME_MINOR -> !otherServerVersions.equals(NEVER);
                    case NEWER_MINOR -> otherServerVersions.equals(ALL_NEWER_VERSIONS);
                    default -> false;
                };
        if (unsupportedServerVersion && !overridden && !unchangedOnly) {
            for (String line : settingMigrated)
                System.out.println("[Refixes] " + CONFIG_PATH.getFileName() + ": " + line);
            System.out.println("[Refixes] " + refusal(relation, versionLabel));
            disable(config, versionLabel);
            return;
        }
        requireFabricMixinSupport(RefixesMixinPlugin.class.getClassLoader());
        JsonObject runtimeConfig = readConfig(RUNTIME_CONFIG_PATH);
        Set<String> currentToggles = new HashSet<>();
        for (MixinToggle toggle : TOGGLES) currentToggles.add(path(toggle));
        List<String> migrated = new ArrayList<>(settingMigrated);
        migrated.addAll(ConfigMigration.migrateMixins(config, currentToggles));
        List<String> runtimeMigrated = ConfigMigration.migrateRuntime(runtimeConfig);
        try {
            EarlyOptions.initializeOutgoingQueueLimits(runtimeConfig);
        } catch (IllegalStateException failure) {
            throw new IllegalStateException(RUNTIME_CONFIG_PATH + ": " + failure.getMessage(), failure);
        }
        Set<String> required = runtimeDependencies(runtimeConfig);
        validateDependencies(config);
        Set<String> selected = selection(config, required, Set.of());
        Map<String, List<String>> skipped = Map.of();
        boolean sameMinor = relation == ServerVersion.Relation.NEWER_SAME_MINOR;
        if (unchangedOnly) {
            TargetFingerprints.Baseline baseline;
            TargetFingerprints.Result result;
            try {
                baseline = TargetFingerprints.baseline();
                result = TargetFingerprints.compare(baseline, selected, TargetFingerprints.mixinBytecode());
            } catch (IOException | RuntimeException | LinkageError | ServiceNotAvailableError failure) {
                System.out.println("[Refixes] Could not compare the Hytale " + versionLabel
                        + " code with the code Refixes was built for (" + failure + ").");
                failure.printStackTrace(System.out);
                disable(config, versionLabel);
                return;
            }
            skipped = skippedSwitches(config, required, result.changes(), sameMinor);
            int switches = 0;
            for (MixinToggle toggle : TOGGLES) {
                if (toggleEnabled(config, toggle)
                        && !toggleMixins(toggle, required).isEmpty()) switches++;
            }
            selected = selection(config, required, skipped.keySet());
            printUnchanged(versionLabel, result, switches, skipped);
        }
        enabledMixins = selected;
        EarlyOptions.setMixinSelection(selected);
        EarlyOptions.setPatchCounters(toggleEnabled(config, "Diagnostics", "PatchCounters"));
        EarlyOptions.setVersionGate(
                (unchangedOnly ? "unchanged:" : overridden ? "overridden:" : "passed:") + versionLabel);
        EarlyOptions.setChangedSwitches(changedSwitches(config));
        EarlyOptions.setSkippedSwitches(List.copyOf(skipped.keySet()));
        initialized = true;
        if (overridden) {
            System.out.println("[Refixes] Version gate overridden for Hytale " + versionLabel + " by "
                    + OTHER_SERVER_VERSIONS + "=" + ALL_PATCHES + ". Applying configured mixins.");
        }
        for (String line : migrated) System.out.println("[Refixes] " + CONFIG_PATH.getFileName() + ": " + line);
        for (String line : runtimeMigrated)
            System.out.println("[Refixes] " + RUNTIME_CONFIG_PATH.getFileName() + ": " + line);
        writeConfig(CONFIG_PATH, serializeMixins(config));
        if (!runtimeMigrated.isEmpty()) writeConfig(RUNTIME_CONFIG_PATH, runtimeConfig);
        System.out.println("[Refixes] === Early mixin patches ===");
        for (String mixin : ALL_MIXINS.stream().sorted().toList())
            System.out.println("[Refixes]   - " + (selected.contains(mixin) ? "[x] " : "[ ] ") + mixin);
        if (EarlyOptions.patchCountersEnabled())
            System.out.println("[Refixes] Patch counters are on. Run /patchcounters to read them.");
    }

    private void disable(JsonObject config, String versionLabel) {
        EarlyOptions.setMixinSelection(Set.of());
        EarlyOptions.setVersionGate("disabled:" + versionLabel);
        EarlyOptions.setChangedSwitches(changedSwitches(config));
        initialized = true;
        System.out.println("[Refixes] Early mixins disabled for Hytale " + versionLabel + ". Refixes supports Hytale "
                + TARGET_SERVER_VERSION + ". Update Refixes for this server version.");
    }

    private static String refusal(ServerVersion.Relation relation, String versionLabel) {
        String target = ", the version this Refixes build was made for.";
        return switch (relation) {
            case OLDER ->
                "Hytale " + versionLabel + " is older than " + TARGET_SERVER_VERSION + target
                        + " Early patches apply on older versions only when " + OTHER_SERVER_VERSIONS + " is "
                        + ALL_PATCHES + ".";
            case NEWER_SAME_MINOR ->
                "Hytale " + versionLabel + " is newer than " + TARGET_SERVER_VERSION + target + " "
                        + OTHER_SERVER_VERSIONS + " is " + NEVER + ".";
            case NEWER_MINOR ->
                "Hytale " + versionLabel + " is newer than " + TARGET_SERVER_VERSION + target
                        + " On versions after Hytale " + ServerVersion.minor(TARGET_SERVER_VERSION)
                        + " early patches apply only when " + OTHER_SERVER_VERSIONS + " is " + ALL_NEWER_VERSIONS
                        + " or " + ALL_PATCHES + ".";
            default ->
                "Refixes cannot compare the Hytale version " + versionLabel + " with " + TARGET_SERVER_VERSION + target;
        };
    }

    private static String otherServerVersions(JsonObject config) {
        JsonElement value = config.get(OTHER_SERVER_VERSIONS);
        return value != null
                        && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()
                        && OTHER_SERVER_VERSIONS_VALUES.contains(value.getAsString())
                ? value.getAsString()
                : OTHER_SERVER_VERSIONS_DEFAULT;
    }

    private static Set<String> toggleMixins(MixinToggle toggle, Set<String> required) {
        Set<String> mixins = new LinkedHashSet<>();
        for (String mixin : toggle.mixins) {
            if (!toggle.runtimeDependency || required.contains(mixin)) mixins.add(mixin);
        }
        mixins.addAll(toggle.uses);
        return mixins;
    }

    private static Set<String> selection(JsonObject config, Set<String> required, Set<String> skipped) {
        Set<String> enabled = new HashSet<>();
        for (MixinToggle toggle : TOGGLES) {
            if (toggleEnabled(config, toggle) && !skipped.contains(path(toggle)))
                enabled.addAll(toggleMixins(toggle, required));
        }
        return Set.copyOf(enabled);
    }

    private static Map<String, List<String>> skippedSwitches(
            JsonObject config, Set<String> required, Map<String, List<String>> changes, boolean sameMinor) {
        Map<String, List<String>> skipped = new LinkedHashMap<>();
        for (MixinToggle toggle : TOGGLES) {
            if (!toggleEnabled(config, toggle)) continue;
            Set<String> mixins = toggleMixins(toggle, required);
            if (mixins.isEmpty()) continue;
            if (SAME_VERSION_CATEGORIES.contains(toggle.jsonPath[1])) {
                skipped.put(path(toggle), List.of(SAME_VERSION_REASON));
                continue;
            }
            String runtimeRecovery = RUNTIME_RECOVERY_SWITCHES.get(path(toggle));
            if (runtimeRecovery != null) {
                skipped.put(
                        path(toggle),
                        List.of("it " + runtimeRecovery + " for a Runtime.Recovery feature, and Recovery switches"
                                + " apply only on the version Refixes was built for"));
                continue;
            }
            WorldData worldData = WORLD_DATA_SWITCHES.get(path(toggle));
            if (worldData != null && worldData.destructive()) {
                skipped.put(
                        path(toggle),
                        List.of("it " + worldData.reason() + ", and switches that delete, move or rewrite world data"
                                + " apply only on Hytale " + TARGET_SERVER_VERSION));
                continue;
            }
            if (worldData != null && !sameMinor) {
                skipped.put(
                        path(toggle),
                        List.of("it " + worldData.reason() + ", and switches that write, load or order world data"
                                + " apply only on Hytale " + ServerVersion.minor(TARGET_SERVER_VERSION) + " versions"));
                continue;
            }
            List<String> reasons = new ArrayList<>();
            for (String mixin : mixins.stream().sorted().toList()) {
                for (String change : changes.getOrDefault(mixin, List.of())) reasons.add(mixin + ": " + change);
            }
            if (!reasons.isEmpty()) skipped.put(path(toggle), reasons);
        }
        for (boolean cascaded = true; cascaded; ) {
            cascaded = false;
            for (Dependency dependency : DEPENDENCIES) {
                if (skipped.containsKey(dependency.dependency())
                        && !skipped.containsKey(dependency.feature())
                        && toggleEnabled(config, toggle(dependency.feature()))) {
                    skipped.put(
                            dependency.feature(), List.of("needs " + dependency.dependency() + ", which is skipped"));
                    cascaded = true;
                }
            }
        }
        return skipped;
    }

    private static void printUnchanged(
            String versionLabel, TargetFingerprints.Result result, int switches, Map<String, List<String>> skipped) {
        System.out.println("[Refixes] Hytale " + versionLabel + " is newer than " + TARGET_SERVER_VERSION
                + ", the version this Refixes build was made for. Applied " + (switches - skipped.size())
                + " patches whose Hytale code is unchanged, skipped " + skipped.size() + ".");
        for (Map.Entry<String, List<String>> entry : skipped.entrySet()) {
            System.out.println("[Refixes]   - " + entry.getKey());
            for (String reason : entry.getValue()) System.out.println("[Refixes]       " + reason);
        }
        for (Map.Entry<String, List<String>> entry : result.changes().entrySet()) {
            if (!entry.getKey().startsWith(TargetFingerprints.PLUGIN_GROUP)) continue;
            System.out.println("[Refixes] Hytale members that changed for runtime plugin class "
                    + entry.getKey().substring(TargetFingerprints.PLUGIN_GROUP.length()) + ":");
            for (String change : entry.getValue()) System.out.println("[Refixes]       " + change);
        }
    }

    private static String readServerVersion() {
        try {
            var resource = RefixesMixinPlugin.class.getClassLoader().getResource("com/hypixel/hytale/Main.class");
            if (resource == null || !(resource.openConnection() instanceof JarURLConnection connection)) return null;
            connection.setUseCaches(false);
            try (var jar = connection.getJarFile()) {
                var manifest = jar.getManifest();
                if (manifest == null) return null;
                String version = manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
                return version == null ? null : version.trim();
            }
        } catch (IOException | SecurityException failure) {
            return null;
        }
    }

    private static Set<String> runtimeDependencies(JsonObject runtimeConfig) {
        Set<String> required = new HashSet<>();
        boolean cleaner = getBoolean(runtimeConfig, "Runtime", "Recovery", "UnknownBlockCleaner", "Enabled");
        if (cleaner && getBoolean(runtimeConfig, "Runtime", "Recovery", "UnknownBlockCleaner", "ScanContainers"))
            required.add("MixinBlockComponentSectionAccess");
        if (cleaner && getBoolean(runtimeConfig, "Runtime", "Recovery", "UnknownBlockCleaner", "ScanFluids"))
            required.add("MixinFluidSection");
        if (getBoolean(runtimeConfig, "Runtime", "Recovery", "Watchdog", "AutoRestartWorlds"))
            required.add("MixinTickingThreadAccess");
        if (getBoolean(runtimeConfig, "Runtime", "Tuning", "AiTickThrottler", "Enabled")) {
            required.add("MixinThrottleFreezeSave");
            required.add("MixinFrozenOwnership");
            required.add("MixinMotionControllerAccess");
        }
        if (getBoolean(runtimeConfig, "Runtime", "Tuning", "IdleWorldPause", "Enabled"))
            required.add("MixinWorldPauseOwner");
        if (getBoolean(runtimeConfig, "Runtime", "Tuning", "PerPlayerHotRadius", "MemoryGuardEnabled"))
            required.add("MixinServerViewRadius");
        boolean idlePlayer = getBoolean(runtimeConfig, "Runtime", "Tuning", "IdlePlayerHandler", "Enabled");
        if (idlePlayer
                || !Boolean.FALSE.equals(
                        getOptionalBoolean(runtimeConfig, "Runtime", "Tuning", "PerPlayerHotRadius", "Enabled")))
            required.add("MixinChunkTrackerRadiusWrites");
        if (idlePlayer) required.add("MixinPlayerRadiusWrites");
        return required;
    }

    private static void validateDependencies(JsonObject config) {
        for (Dependency dependency : DEPENDENCIES) {
            if (toggleEnabled(config, toggle(dependency.feature())))
                requireDependency(
                        toggleEnabled(config, toggle(dependency.dependency())),
                        dependency.feature(),
                        dependency.dependency());
        }
    }

    private static MixinToggle toggle(String path) {
        for (MixinToggle toggle : TOGGLES) {
            if (path(toggle).equals(path)) return toggle;
        }
        throw new IllegalArgumentException("Unknown mixin policy: " + path);
    }

    private static void requireDependency(boolean available, String feature, String dependency) {
        if (!available)
            throw new IllegalStateException(feature + " requires " + dependency + " to be enabled in " + CONFIG_PATH);
    }

    private static boolean toggleEnabled(JsonObject config, String category, String key) {
        for (MixinToggle toggle : TOGGLES) {
            String[] path = toggle.jsonPath;
            if (path.length == 3 && path[1].equals(category) && path[2].equals(key))
                return toggleEnabled(config, toggle);
        }
        throw new IllegalArgumentException("Unknown mixin policy: " + category + "." + key);
    }

    private static boolean toggleEnabled(JsonObject config, MixinToggle toggle) {
        Boolean value = getOptionalBoolean(config, toggle.jsonPath);
        return value == null ? toggle.defaultEnabled : value;
    }

    private static List<String> changedSwitches(JsonObject config) {
        List<String> changed = new ArrayList<>();
        if (!otherServerVersions(config).equals(OTHER_SERVER_VERSIONS_DEFAULT))
            changed.add(OTHER_SERVER_VERSIONS + "=" + otherServerVersions(config));
        for (MixinToggle toggle : TOGGLES) {
            boolean enabled = toggleEnabled(config, toggle);
            if (enabled != toggle.defaultEnabled) changed.add(path(toggle) + "=" + enabled);
        }
        return changed;
    }

    private static void requireFabricMixinSupport(ClassLoader loader) {
        int compatibility;
        try {
            Class<?> fabricUtil = Class.forName("org.spongepowered.asm.mixin.FabricUtil", false, loader);
            compatibility = fabricUtil.getField("COMPATIBILITY_LATEST").getInt(null);
        } catch (ReflectiveOperationException | LinkageError | SecurityException | IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Refixes requires Fabric Mixin 0.17.5 or newer. Update Hyinit and remove conflicting Mixin JARs.",
                    failure);
        }
        if (compatibility < 17005)
            throw new IllegalStateException("Refixes requires Fabric Mixin 0.17.5 or newer (detected compatibility "
                    + compatibility + "). Update Hyinit and remove older conflicting Mixin runtime JARs.");
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!initialized) return false;
        String simpleName = mixinClassName;
        if (mixinPackage != null && mixinClassName.startsWith(mixinPackage + "."))
            simpleName = mixinClassName.substring(mixinPackage.length() + 1);
        return enabledMixins.contains(simpleName);
    }

    private static JsonObject readConfig(Path path) {
        if (!Files.isRegularFile(path)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (!element.isJsonObject())
                throw new IllegalStateException("Malformed Refixes config at " + path + ": root must be a JSON object");
            return element.getAsJsonObject();
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Malformed Refixes config at " + path + ": " + failure.getMessage(), failure);
        }
    }

    private static JsonObject serializeMixins(JsonObject config) {
        JsonObject mixins = new JsonObject();
        for (String category : CATEGORIES) mixins.add(category, new JsonObject());
        for (MixinToggle toggle : TOGGLES)
            setBoolean(
                    mixins,
                    java.util.Arrays.copyOfRange(toggle.jsonPath, 1, toggle.jsonPath.length),
                    toggleEnabled(config, toggle));
        config.addProperty(OTHER_SERVER_VERSIONS, otherServerVersions(config));
        config.add("Mixins", mixins);
        return config;
    }

    private static void writeConfig(Path path, JsonObject config) {
        String content = new GsonBuilder().setPrettyPrinting().create().toJson(config);
        try {
            if (Files.isRegularFile(path)
                    && Files.readString(path, StandardCharsets.UTF_8).equals(content)) return;
            Files.createDirectories(path.getParent());
            Path temporary =
                    Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
            try {
                Files.writeString(temporary, content, StandardCharsets.UTF_8);
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException failure) {
            System.out.println("[Refixes] WARNING: could not update " + path + " (" + failure
                    + "). The selected patches are applied; the file keeps its previous content.");
            failure.printStackTrace(System.out);
        }
    }

    private static boolean getBoolean(JsonObject root, String... path) {
        return Boolean.TRUE.equals(getOptionalBoolean(root, path));
    }

    private static Boolean getOptionalBoolean(JsonObject root, String... path) {
        JsonElement current = root;
        for (String part : path) {
            if (current == null || !current.isJsonObject()) return null;
            current = current.getAsJsonObject().get(part);
        }
        return current != null
                        && current.isJsonPrimitive()
                        && current.getAsJsonPrimitive().isBoolean()
                ? current.getAsBoolean()
                : null;
    }

    private static void setBoolean(JsonObject root, String[] path, boolean value) {
        JsonObject current = root;
        for (int i = 0; i < path.length - 1; i++) {
            JsonElement child = current.get(path[i]);
            if (child == null || !child.isJsonObject()) {
                JsonObject created = new JsonObject();
                current.add(path[i], created);
                current = created;
            } else current = child.getAsJsonObject();
        }
        current.addProperty(path[path.length - 1], value);
    }
}
