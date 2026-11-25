package cc.irori.refixes.early;

import cc.irori.refixes.version.HytaleLine;
import cc.irori.refixes.version.HytaleVersions;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

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
                    "Mixins.Correctness.FluidChangeSaving",
                    additive("only marks sections dirty when their fluids change")),
            Map.entry(
                    "Mixins.Optimizations.SkipUnchangedLightSaves",
                    destructive("skips section saves the light pass would have queued")),
            Map.entry(
                    "Mixins.Correctness.ChunkStoreSectionBackoffDeadlock",
                    additive("only avoids a lock deadlock when a chunk load fails")),
            Map.entry(
                    "Mixins.Correctness.EntitySectionLoad",
                    additive("only stops duplicate section loads and stale section moves for entities")),
            Map.entry(
                    "Mixins.Correctness.SerializableArchetype",
                    additive("only fixes the start index of the saved component list without changing which"
                            + " components are saved")),
            Map.entry(
                    "Mixins.Correctness.StoredFlockSaving",
                    additive("only marks spawn markers and their flock sections dirty")),
            Map.entry(
                    "Mixins.Correctness.WorldEventSaving",
                    additive("only orders world event saves and waits for the last one at shutdown")),
            Map.entry(
                    "Mixins.Correctness.RocksDbFlush",
                    additive("only flushes every RocksDB column family before closing")),
            Map.entry(
                    "Mixins.Correctness.RocksDbColumnRemoval",
                    destructive("range deletes RocksDB columns and their stored entities")),
            Map.entry(
                    "Mixins.Correctness.RocksDbScanBounds",
                    destructive("changes which stored RocksDB rows are read when chunks load")),
            Map.entry(
                    "Mixins.Optimizations.RocksDbCompression",
                    destructive("changes the RocksDB compression that chunks are stored with")),
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
                    additive("only keeps the origin chunk loaded so it is never unloaded early")),
            Map.entry(
                    "Mixins.Tuning.WorldMapExistingTerrain",
                    additive("only reads chunks that already exist for the world map and never generates or rewrites"
                            + " them")),
            Map.entry(
                    "Mixins.Correctness.WorldGenReload",
                    destructive("changes how the world generation reload deletes stored chunks and when saving"
                            + " resumes")),
            Map.entry(
                    "Mixins.Correctness.WorldTransfer", destructive("moves players and entities between world saves")),
            Map.entry(
                    "Mixins.Correctness.WorldOwnership",
                    additive("only makes each world register and stop once by its owner")),
            Map.entry(
                    "Mixins.Correctness.WorldReopenGuard",
                    additive("only waits for a removed world to release its folder before reopening it")),
            Map.entry(
                    "Mixins.Correctness.WorldRetirement",
                    destructive("deletes removed world folders after their storage goes idle")),
            Map.entry(
                    "Mixins.Correctness.WorldTaskQueueClose",
                    additive("only cancels world tasks that could never run after shutdown so waiters do not hang")),
            Map.entry(
                    "Mixins.Correctness.UniverseDeletedCleanup",
                    additive("only keeps startup going when cleanup of already deleted worlds fails")),
            Map.entry(
                    "Mixins.Correctness.ServerConfigSaveChain",
                    additive("only runs server config saves one after another")),
            Map.entry("Mixins.Backports.ShutdownRemoval", destructive("deletes delete-on-remove worlds at shutdown")),
            Map.entry(
                    "Mixins.Backports.ShutdownSaveFailure",
                    additive("only finishes shutdown after a chunk save fails")),
            Map.entry(
                    "Mixins.Compatibility.AiTickThrottler",
                    destructive("strips Frozen components from the NPC data it writes")),
            Map.entry(
                    "Mixins.Optimizations.CodecMetadata",
                    destructive("changes codec metadata handling while data is saved and loaded")),
            Map.entry(
                    "Mixins.Correctness.TickingThreadAbandon",
                    additive("only makes sure a stuck world runs its shutdown save exactly once")),
            Map.entry(
                    "Mixins.Correctness.PrefabPasteLoaded",
                    destructive("cancels prefab pastes and removes prefab editing worlds, whose removal deletes their"
                            + " folders")),
            Map.entry(
                    "Mixins.Correctness.PortalDeviceWorldClose",
                    destructive("changes when portal worlds are removed, whose removal deletes their folders")));
    private static final Map<String, String> RUNTIME_RECOVERY_SWITCHES = Map.of(
            "Mixins.Compatibility.UnknownBlockCleaner", "lets the runtime cleaner rewrite stored blocks",
            "Mixins.Compatibility.Watchdog", "lets the watchdog save, remove and reload crashed worlds");
    private static final List<String> CATEGORIES = List.of(
            "Correctness",
            "Backports",
            "Optimizations",
            "Tuning",
            "Features",
            "Recovery",
            "Compatibility",
            "Diagnostics",
            "Experimental");

    private static final List<String> TARGET_SERVER_VERSIONS =
            Arrays.stream(HytaleLine.values()).map(HytaleLine::target).toList();

    private static volatile String targetVersion = TARGET_SERVER_VERSIONS.getFirst();

    private record MixinToggle(
            String[] jsonPath,
            boolean defaultEnabled,
            List<String> mixins,
            boolean runtimeDependency,
            List<String> uses) {}

    enum Gate {
        ALL,
        OVERRIDDEN,
        UNCHANGED,
        DISABLED
    }

    private record Dependency(String feature, String dependency) {}

    private static final String HYINIT_PLUGIN_ID = "IroriPowered:Refixes";

    private static final int REQUIRED_HYINIT_API = 2;
    private static final String REQUIRED_HYINIT = "0.5.0";

    private static final List<Dependency> DEPENDENCIES = List.of(
            new Dependency("Mixins.Experimental.Parallel.CollectVisible", "Mixins.Correctness.ParallelTaskLifecycle"),
            new Dependency("Mixins.Correctness.OutgoingQueueLimits", "Mixins.Correctness.SetupPacing"),
            new Dependency("Mixins.Features.SharedInstances", "Mixins.Features.SharedInstanceStorage"),
            new Dependency("Mixins.Optimizations.SkipUnchangedLightSaves", "Mixins.Correctness.BlockChangeSaving"));

    private static final List<Dependency> DEPENDENCIES_07 = List.of(
            new Dependency("Mixins.Correctness.WorldTransfer", "Mixins.Correctness.StorageOrdering"),
            new Dependency("Mixins.Correctness.WorldRetirement", "Mixins.Correctness.WorldReopenGuard"),
            new Dependency("Mixins.Optimizations.SkipUnchangedLightSaves", "Mixins.Correctness.FluidChangeSaving"));

    private static final List<Dependency> DEPENDENCIES_08 = List.of();

    private static volatile List<Dependency> dependencies = DEPENDENCIES;

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
                    "MixinFloodLightNeutralV08",
                    "MixinFluidSectionLightNeutral",
                    "MixinBlockTypeAssetMapLightGeneration"),
            toggle(
                    "Optimizations",
                    "SkipUnchangedLightSaves",
                    false,
                    "MixinFloodLightSaveMarks",
                    "MixinBlockSectionStoredLight",
                    "MixinChunkLightDataAccess"),
            toggle("Optimizations", "RocksDbCompression", false, "MixinRocksDbCompression"),
            toggle(
                    "Optimizations",
                    "SoundHeaderValidation",
                    true,
                    "MixinSoundFileValidators",
                    "MixinSoundEventLayer",
                    "MixinCommonAssetBlobAccess",
                    "MixinOggVorbisInfoCacheAccess",
                    "MixinOggVorbisInfoAccess"),
            toggle(
                    "Optimizations",
                    "AssetPartSnapshotSharing",
                    false,
                    "MixinPacketHandlerAssetParts",
                    "MixinCachedPacketAccess"),
            toggle("Optimizations", "NPCComponentType", false, "MixinNPCComponentType"),
            toggle("Optimizations", "DefaultMovementResistance", false, "MixinDefaultMovementResistance"),
            toggle("Optimizations", "CodecMetadata", true, "MixinCodecMetadata"),
            toggle("Diagnostics", "HytaleServer", true, "MixinHytaleServer"),
            toggle("Diagnostics", "PatchCounters", false),
            toggle("Tuning", "CylinderVisibility", false, "MixinCollectVisible"),
            toggle("Tuning", "ChunkStreaming", false, "MixinPlayerChunkTrackerSystems", "MixinPlayerViewRadius"),
            toggle("Tuning", "WorldHeightBounds", false, "MixinChunkTrackerHeightBounds", "MixinWorldHeightBounds"),
            toggle(
                    "Tuning",
                    "WorldMapExistingTerrain",
                    false,
                    "MixinImageBuilderExistingTerrain",
                    "MixinWorldMapTrackerMissingTerrain",
                    "MixinWorldMapTrackerMissingTerrainV08",
                    "MixinWorldMapImageTrackerMissingTerrain",
                    "MixinChunkGridExistingTerrain",
                    "MixinChunkGridExistingTerrainV08",
                    "MixinLoadStateAccess"),
            toggle("Tuning", "TickWait", true, "MixinTickSleep"),
            toggle("Tuning", "PathfindingLimits", false, "MixinAStarLimits"),
            toggle("Features", "OriginRetention", false, "MixinChunkUnloadingSystem", "MixinSectionUnloadingSystem"),
            toggle(
                    "Features",
                    "SharedInstances",
                    false,
                    "MixinInstancesPlugin",
                    "MixinSharedInstanceResetWorld",
                    "MixinSharedInstanceResetWorldV08",
                    "MixinSharedInstanceResetRemoval",
                    "MixinSharedInstanceResetRemovalV08",
                    "MixinSharedInstanceResetUniverse",
                    "MixinSharedInstanceResetUniverseV08"),
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
                    "MixinNPCRoleActivationV08",
                    "MixinUnresolvedNPCRoleChange",
                    "MixinUnresolvedNPCRoleChangeV08",
                    "MixinRoleChangeRequestAccess",
                    "MixinRoleChangeFlagAccess",
                    "MixinBuilderDependencyAccess",
                    "MixinBuilderValidationReferences"),
            toggle("Recovery", "InvalidCollisionData", false, "MixinCollisionModule"),
            toggle("Correctness", "MountPlugin", true, "MixinMountPlugin"),
            toggle(
                    "Correctness",
                    "PacketAdapters",
                    true,
                    "MixinPacketHandlerInbound",
                    "MixinPlayerChannelHandlerInbound"),
            toggle("Correctness", "RocksDbFlush", true, "MixinRocksDbFlush", "MixinRocksDbSaverFlush"),
            toggle("Correctness", "WorldGenReload", true, "MixinWorldGenReload"),
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
                    "MixinChunkTrackerNotificationResyncV08",
                    "MixinBlockChunkNotificationPacket",
                    "MixinEnvironmentNotificationPacket",
                    "MixinWorldNotificationHandler",
                    "MixinPacketHandlerSectionFence"),
            toggle("Correctness", "WorldMapPacing", true, "MixinWorldMapPacing"),
            toggle("Recovery", "InteractionChain", false, "MixinInteractionChain"),
            toggle("Correctness", "MarkerAddRemoveSystem", true, "MixinMarkerAddRemoveSystem"),
            toggle(
                    "Correctness",
                    "TickingSpawnMarkerSystem",
                    true,
                    "MixinTickingSpawnMarkerSystem",
                    "MixinTickingSpawnMarkerSystemV08"),
            toggle("Recovery", "WorldMapTracker", false, "MixinWorldMapTracker"),
            toggle("Correctness", "WorldMapCache", true, "MixinWorldMapManager"),
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
            runtimeToggle("ChunkLoader", "MixinChunkUnloadCommand"),
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
            toggle("Correctness", "WorldEventSaving", true, "MixinGlobalWorldEventManager"),
            toggle(
                    "Correctness",
                    "WorldTransfer",
                    true,
                    "MixinWorldTransfer",
                    "MixinPlayerTeleport",
                    "MixinCubicEntityTransfer",
                    "MixinEntityTransferAdmission",
                    "MixinEntityTransferAdmissionV08",
                    "MixinUniverseResetOwnership",
                    "MixinPlayerResetCommand",
                    "MixinCubicTransferSaver",
                    "MixinCubicTransferSection",
                    "MixinCubicTransferMove",
                    "MixinCubicTransferColumn",
                    "MixinCubicTransferTickingColumn",
                    "MixinCubicTransferRemoval",
                    "MixinCubicTransferShutdown"),
            toggle("Correctness", "WorldReopenGuard", true, "MixinUniverseRetirement", "MixinUniverseTransferCleanup"),
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
            toggle("Correctness", "EntityModule", true, "MixinEntityModule"),
            toggle("Correctness", "EffectConditionClock", true, "MixinLivingEntityEffectSystem"),
            toggle("Correctness", "AssetInheritance", true, "MixinAssetStoreInheritance"),
            toggle("Correctness", "NPCKillsEntitySystem", true, "MixinNPCKillsEntitySystem"),
            toggle("Correctness", "PortalDeviceSummonPage", true, "MixinPortalDeviceSummonPage"),
            toggle("Correctness", "RemovalSystem", true, "MixinRemovalSystem", "MixinPortalWorldAccessor"),
            toggle("Correctness", "StateSupport", true, "MixinStateSupport"),
            toggle("Correctness", "DeployableOwnerComponent", true, "MixinDeployableOwnerComponent"),
            toggle("Correctness", "TeleportToPlayerCommand", true, "MixinTeleportToPlayerCommand"),
            toggle("Correctness", "TeleportInstanceInteraction", true, "MixinTeleportInstanceInteraction"),
            toggle("Correctness", "TickingThreadStop", true, "MixinTickingThreadStop"),
            using(
                    toggle("Correctness", "TickingThreadStart", true, "MixinTickingThreadStart"),
                    "MixinTickingThreadRunAccess"),
            toggle("Correctness", "SerializableArchetype", true, "MixinSerializableArchetype"),
            using(
                    toggle(
                            "Correctness",
                            "WorldOwnership",
                            true,
                            "MixinUniverseWorldOwnership",
                            "MixinUniverseWorldOwnershipV08",
                            "MixinWorldOwnership",
                            "MixinWorldOwnershipV08",
                            "MixinPortalInvalidDestinationSystem"),
                    "MixinTickingThreadAccess"),
            toggle(
                    "Correctness",
                    "WorldRetirement",
                    false,
                    "MixinWorldRetirement",
                    "MixinInstancesPluginRetirement",
                    "MixinCreativeHubPluginRetirement"),
            toggle("Experimental", "Parallel", "CollectVisible", false, "MixinCollectVisibleParallel"),
            toggle(
                    "Experimental",
                    "PathfindingBudget",
                    false,
                    "MixinBodyMotionFindBase",
                    "MixinWorldPathfindingBudget"),
            toggle(
                    "Correctness",
                    "StoredFlockSaving",
                    true,
                    "MixinSpawnMarkerTicking",
                    "MixinSpawnMarkerSpawnSaving",
                    "MixinSpawnReferenceSaving"),
            toggle("Correctness", "FluidChangeSaving", true, "MixinFluidChangeSaving"),
            toggle("Correctness", "FluidRelightSection", true, "MixinFluidRelightSection"),
            toggle("Correctness", "ServerConfigSaveChain", true, "MixinServerConfigSaveChain"),
            using(
                    toggle("Correctness", "TickingThreadAbandon", true, "MixinTickingThreadAbandon"),
                    "MixinTickingThreadRunAccess"),
            toggle("Correctness", "WorldTaskQueueClose", true, "MixinWorldTaskQueueClose"),
            toggle("Correctness", "ServerShutdownWatchdog", true, "MixinServerShutdownWatchdog"),
            toggle("Correctness", "WorldMapTickingStop", true, "MixinWorldMapTickingStop"),
            toggle("Correctness", "UniverseDeletedCleanup", true, "MixinUniverseDeletedCleanup"),
            toggle("Correctness", "MountMovementTeleport", true, "MixinMountMovementTeleport"),
            toggle("Correctness", "CommonAssetHashCleanup", true, "MixinCommonAssetHashCleanup"),
            toggle("Correctness", "ObjectiveEntityRemoval", true, "MixinSpawnEntityTransactionRecord"),
            toggle("Correctness", "FluidDropSection", true, "MixinFluidTickerDropSection"),
            toggle("Correctness", "SoundChannelEncoding", true, "MixinSoundFileEncoding"),
            toggle("Correctness", "MessageSelect", true, "MixinMessageSelect"),
            toggle("Correctness", "ServerManagerShutdown", true, "MixinServerManagerShutdown"),
            toggle(
                    "Correctness",
                    "EntitySectionLoad",
                    true,
                    "MixinEntitySectionLoad",
                    "MixinEntitySectionLoadSpawn",
                    "MixinTransformSectionLoad"),
            using(
                    toggle("Correctness", "PrefabPasteLoaded", true, "MixinPrefabPasteLoaded"),
                    "MixinPrefabPendingPasteAccess"),
            toggle(
                    "Correctness",
                    "MapMarkerLocking",
                    true,
                    "MixinMapMarkerLocking",
                    "MixinMapMarkerLocking$Provider",
                    "MixinMapMarkerLocking$Reveal"),
            toggle("Correctness", "PortalDeviceWorldClose", true, "MixinPortalDeviceWorldClose"),
            toggle("Correctness", "RocksDbColumnRemoval", true, "MixinRocksDbColumnRemoval"),
            toggle(
                    "Correctness",
                    "RocksDbScanBounds",
                    true,
                    "MixinRocksDbScanBounds",
                    "MixinRocksDbBlockComponentScanBounds"),
            toggle("Backports", "ShutdownRemoval", true, "MixinShutdownWorldRemoval", "MixinWorldAliveAccess"),
            toggle("Backports", "ShutdownSaveFailure", true, "MixinShutdownChunkSaveFailure"),
            toggle("Backports", "StaticLayers", true, "MixinStaticLayerGeneration"),
            toggle("Backports", "ModelParticleScale", true, "MixinModelParticleScale"));

    private static volatile List<MixinToggle> activeToggles = TOGGLES;

    private static final Set<String> ALL_MIXINS = allMixins();

    private static final Set<String> SWITCH_PATHS = switchPaths();

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

    private static Set<String> switchPaths() {
        Set<String> paths = new HashSet<>();
        for (MixinToggle toggle : TOGGLES) paths.add(path(toggle));
        return Set.copyOf(paths);
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
        JsonObject config = readConfig(CONFIG_PATH);
        for (String note : ConfigCheck.check(config, SWITCH_PATHS))
            System.out.println("[Refixes] " + CONFIG_PATH.getFileName() + ": " + note);
        String serverVersion = HytaleVersions.running();
        String versionLabel = serverVersion == null ? "unknown" : serverVersion;
        if (HytaleVersions.hyinitApi() < REQUIRED_HYINIT_API) {
            String hyinit = HytaleVersions.hyinitVersion();
            disable(
                    config,
                    "hyinit:" + (hyinit == null ? "unknown" : hyinit),
                    "Early patches are off because " + (hyinit == null ? "this Hyinit" : "Hyinit " + hyinit)
                            + " is older than " + REQUIRED_HYINIT + ". Update Hyinit to " + REQUIRED_HYINIT
                            + " or newer.");
            return;
        }
        Set<String> unavailable = unavailable(serverVersion);
        activeToggles = activeToggles(unavailable);
        HytaleLine hytaleLine = HytaleLine.nearest(serverVersion);
        List<Dependency> versionDependencies = new ArrayList<>(DEPENDENCIES);
        versionDependencies.addAll(lineDependencies(hytaleLine));
        dependencies = List.copyOf(versionDependencies);
        targetVersion = hytaleLine.target();
        ServerVersion.Relation relation = ServerVersion.gate(hytaleLine, serverVersion);
        Gate gate = gate(serverVersion, otherServerVersions(config));
        boolean overridden = gate == Gate.OVERRIDDEN;
        boolean unchangedOnly = gate == Gate.UNCHANGED;
        if (gate == Gate.DISABLED) {
            disable(config, "disabled:" + versionLabel, refusal(relation, versionLabel), unsupported(versionLabel));
            return;
        }
        requireFabricMixinSupport(RefixesMixinPlugin.class.getClassLoader());
        JsonObject runtimeConfig = readConfig(RUNTIME_CONFIG_PATH);
        try {
            EarlyOptions.initializeOutgoingQueueLimits(runtimeConfig);
        } catch (IllegalStateException failure) {
            throw new IllegalStateException(RUNTIME_CONFIG_PATH + ": " + failure.getMessage(), failure);
        }
        Set<String> required = runtimeDependencies(runtimeConfig);
        JsonObject effective = withoutUnmetDependencies(config);
        Set<String> selected = without(selection(effective, required, Set.of()), unavailable);
        Map<String, List<String>> skipped = Map.of();
        boolean sameMinor = relation == ServerVersion.Relation.NEWER_SAME_MINOR;
        if (unchangedOnly) {
            Map<String, List<String>> changes;
            try {
                changes = hyinitChanges(mixinPackage + ".", selected);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                System.out.println("[Refixes] Could not compare the Hytale " + versionLabel
                        + " code with the code Refixes was built for (" + failure + ").");
                failure.printStackTrace(System.out);
                disable(config, "disabled:" + versionLabel, unsupported(versionLabel));
                return;
            }
            skipped = skippedSwitches(effective, required, changes, sameMinor);
            int switches = 0;
            for (MixinToggle toggle : activeToggles) {
                if (toggleEnabled(effective, toggle)
                        && !toggleMixins(toggle, required).isEmpty()) switches++;
            }
            selected = without(selection(effective, required, skipped.keySet()), unavailable);
            printUnchanged(versionLabel, switches, skipped);
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
        writeConfig(CONFIG_PATH, serializeMixins(config));
        System.out.println("[Refixes] === Early mixin patches ===");
        for (String mixin : without(ALL_MIXINS, unavailable).stream().sorted().toList())
            System.out.println("[Refixes]   - " + (selected.contains(mixin) ? "[x] " : "[ ] ") + mixin);
        if (EarlyOptions.patchCountersEnabled())
            System.out.println("[Refixes] Patch counters are on. Run /patchcounters to read them.");
    }

    private void disable(JsonObject config, String gateState, String... lines) {
        EarlyOptions.setMixinSelection(Set.of());
        EarlyOptions.setVersionGate(gateState);
        EarlyOptions.setChangedSwitches(changedSwitches(config));
        initialized = true;
        for (String line : lines) System.out.println("[Refixes] " + line);
    }

    private static String unsupported(String versionLabel) {
        return "Early mixins disabled for Hytale " + versionLabel + ". Refixes supports Hytale "
                + String.join(" and ", TARGET_SERVER_VERSIONS) + ". Update Refixes for this server version.";
    }

    private static String refusal(ServerVersion.Relation relation, String versionLabel) {
        String madeFor = ", the version this Refixes build was made for.";
        return switch (relation) {
            case OLDER ->
                "Hytale " + versionLabel + " is older than " + targetVersion + madeFor
                        + " Early patches apply on older versions only when " + OTHER_SERVER_VERSIONS + " is "
                        + ALL_PATCHES + ".";
            case NEWER_SAME_MINOR ->
                "Hytale " + versionLabel + " is newer than " + targetVersion + madeFor + " " + OTHER_SERVER_VERSIONS
                        + " is " + NEVER + ".";
            case NEWER_MINOR ->
                "Hytale " + versionLabel + " is newer than " + targetVersion + madeFor
                        + " On versions after Hytale " + ServerVersion.minor(targetVersion)
                        + " early patches apply only when " + OTHER_SERVER_VERSIONS + " is " + ALL_NEWER_VERSIONS
                        + " or " + ALL_PATCHES + ".";
            default -> "Refixes cannot compare the Hytale version " + versionLabel + " with " + targetVersion + madeFor;
        };
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<String>> hyinitChanges(String prefix, Set<String> mixins)
            throws ReflectiveOperationException {
        Class<?> api = Class.forName(
                "cc.irori.hyinit.shared.HyinitDiagnostics", false, RefixesMixinPlugin.class.getClassLoader());
        return hyinitChanges(
                (Map<String, Object>) api.getMethod("snapshot").invoke(null), targetVersion, prefix, mixins);
    }

    @SuppressWarnings("unchecked")
    static Map<String, List<String>> hyinitChanges(
            Map<String, Object> snapshot, String target, String prefix, Set<String> mixins) {
        int compared = 0;
        for (Map<?, ?> check : (List<Map<?, ?>>) snapshot.get("fingerprintChecks")) {
            if (!HYINIT_PLUGIN_ID.equals(check.get("pluginId"))) continue;
            if (!"compared".equals(check.get("status")) || !"checked".equals(check.get("usedMembers")))
                throw new IllegalStateException("Hyinit could not compare the fingerprints of " + check.get("jar"));
            if (!(check.get("baseline") instanceof String baseline)
                    || ServerVersion.relation(target, baseline) != ServerVersion.Relation.TARGET)
                throw new IllegalStateException("Hyinit compared " + check.get("jar") + " with Hytale "
                        + check.get("baseline") + ", not with Hytale " + target);
            compared++;
        }
        if (compared == 0)
            throw new IllegalStateException(
                    "Hyinit " + snapshot.get("hyinitVersion") + " did not compare the Refixes fingerprints");
        Map<String, List<String>> changes = new LinkedHashMap<>();
        for (Map<?, ?> changed : (List<Map<?, ?>>) snapshot.get("changedTargets")) {
            String mixin = (String) changed.get("mixin");
            if (!mixin.startsWith(prefix)) continue;
            String name = mixin.substring(prefix.length());
            if (!mixins.contains(name)) continue;
            Object member = changed.get("member");
            Object baseline = changed.get("baseline");
            changes.computeIfAbsent(name, unused -> new ArrayList<>())
                    .add(changed.get("kind") + " " + changed.get("class") + (member == null ? "" : "." + member) + " "
                            + changed.get("status") + (baseline == null ? "" : " since Hytale " + baseline));
        }
        return changes;
    }

    static Gate gate(String serverVersion, String otherServerVersions) {
        HytaleLine hytaleLine = HytaleLine.nearest(serverVersion);
        ServerVersion.Relation relation = ServerVersion.gate(hytaleLine, serverVersion);
        if (relation == ServerVersion.Relation.TARGET) return Gate.ALL;
        if (otherServerVersions.equals(ALL_PATCHES) && hytaleLine.covers(serverVersion)) return Gate.OVERRIDDEN;
        boolean unchanged = switch (relation) {
            case NEWER_SAME_MINOR -> !otherServerVersions.equals(NEVER);
            case NEWER_MINOR -> otherServerVersions.equals(ALL_NEWER_VERSIONS);
            default -> false;
        };
        return unchanged ? Gate.UNCHANGED : Gate.DISABLED;
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

    private Set<String> unavailable(String serverVersion) {
        Set<String> unavailable = new HashSet<>();
        ClassLoader loader = RefixesMixinPlugin.class.getClassLoader();
        for (String mixin : ALL_MIXINS) {
            String resource = mixinPackage.replace('.', '/') + "/" + mixin + ".class";
            try (InputStream input = loader.getResourceAsStream(resource)) {
                if (input == null) continue;
                ClassNode node = new ClassNode();
                new ClassReader(input.readAllBytes())
                        .accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                if (!MixinAnnotations.applies(node, serverVersion)) unavailable.add(mixin);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not read " + resource + " from the Refixes JAR", failure);
            }
        }
        return Set.copyOf(unavailable);
    }

    private static List<MixinToggle> activeToggles(Set<String> unavailable) {
        List<MixinToggle> active = new ArrayList<>();
        for (MixinToggle toggle : TOGGLES) {
            if (toggle.mixins.isEmpty() || !unavailable.containsAll(toggle.mixins)) active.add(toggle);
        }
        return List.copyOf(active);
    }

    private static Set<String> without(Set<String> mixins, Set<String> unavailable) {
        Set<String> kept = new HashSet<>(mixins);
        kept.removeAll(unavailable);
        return Set.copyOf(kept);
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
        for (MixinToggle toggle : activeToggles) {
            if (toggleEnabled(config, toggle) && !skipped.contains(path(toggle)))
                enabled.addAll(toggleMixins(toggle, required));
        }
        return Set.copyOf(enabled);
    }

    private static Map<String, List<String>> skippedSwitches(
            JsonObject config, Set<String> required, Map<String, List<String>> changes, boolean sameMinor) {
        Map<String, List<String>> skipped = new LinkedHashMap<>();
        for (MixinToggle toggle : activeToggles) {
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
                                + " apply only on Hytale " + targetVersion));
                continue;
            }
            if (worldData != null && !sameMinor) {
                skipped.put(
                        path(toggle),
                        List.of("it " + worldData.reason() + ", and switches that write, load or order world data"
                                + " apply only on Hytale " + ServerVersion.minor(targetVersion) + " versions"));
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
            for (Dependency dependency : dependencies) {
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

    private static void printUnchanged(String versionLabel, int switches, Map<String, List<String>> skipped) {
        System.out.println("[Refixes] Hytale " + versionLabel + " is newer than " + targetVersion
                + ", the version this Refixes build was made for. Applied " + (switches - skipped.size())
                + " patches whose Hytale code is unchanged, skipped " + skipped.size() + ".");
        for (Map.Entry<String, List<String>> entry : skipped.entrySet()) {
            System.out.println("[Refixes]   - " + entry.getKey());
            for (String reason : entry.getValue()) System.out.println("[Refixes]       " + reason);
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
        if (!Boolean.FALSE.equals(getOptionalBoolean(runtimeConfig, "Runtime", "Features", "ChunkLoader", "Enabled")))
            required.add("MixinChunkUnloadCommand");
        return required;
    }

    private static List<Dependency> lineDependencies(HytaleLine line) {
        return switch (line) {
            case V07 -> DEPENDENCIES_07;
            case V08 -> DEPENDENCIES_08;
        };
    }

    private static JsonObject withoutUnmetDependencies(JsonObject config) {
        JsonObject effective = config.deepCopy();
        for (boolean cascaded = true; cascaded; ) {
            cascaded = false;
            for (Dependency dependency : dependencies) {
                MixinToggle feature = toggle(dependency.feature());
                if (!activeToggles.contains(feature)
                        || !toggleEnabled(effective, feature)
                        || toggleEnabled(effective, toggle(dependency.dependency()))) continue;
                setBoolean(effective, feature.jsonPath, false);
                System.out.println("[Refixes] " + dependency.feature() + " is off on this start because it needs "
                        + dependency.dependency() + ", which is off. Turn on " + dependency.dependency() + " in "
                        + CONFIG_PATH + " to use it.");
                cascaded = true;
            }
        }
        return effective;
    }

    private static MixinToggle toggle(String path) {
        for (MixinToggle toggle : TOGGLES) {
            if (path(toggle).equals(path)) return toggle;
        }
        throw new IllegalArgumentException("Unknown mixin policy: " + path);
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
        for (MixinToggle toggle : activeToggles) {
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
        config.addProperty(OTHER_SERVER_VERSIONS, otherServerVersions(config));
        JsonObject mixins = config.get("Mixins") instanceof JsonObject existing ? existing : new JsonObject();
        config.add("Mixins", mixins);
        for (String category : CATEGORIES) {
            for (MixinToggle toggle : activeToggles) {
                if (toggle.jsonPath[1].equals(category)) {
                    if (!(mixins.get(category) instanceof JsonObject)) mixins.add(category, new JsonObject());
                    break;
                }
            }
        }
        for (MixinToggle toggle : activeToggles) {
            if (getOptionalBoolean(config, toggle.jsonPath) == null)
                setBoolean(config, toggle.jsonPath, toggle.defaultEnabled);
        }
        return config;
    }

    private static void writeConfig(Path path, JsonObject config) {
        String content = new GsonBuilder().setPrettyPrinting().create().toJson(config);
        try {
            if (Files.isRegularFile(path)) {
                if (Files.readString(path, StandardCharsets.UTF_8).equals(content)) return;
                Files.copy(path, path.resolveSibling(path.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
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
