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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import org.spongepowered.asm.launch.MixinInitialisationError;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

public class RefixesMixinPlugin implements IMixinConfigPlugin {

    private static final Path CONFIG_PATH = Paths.get("mods", "IroriPowered_Refixes", "Refixes.json");
    private static final Path RUNTIME_CONFIG_PATH = CONFIG_PATH.resolveSibling("config.json");
    private static final String ALLOW_UNSUPPORTED_SERVER_VERSION = "AllowUnsupportedServerVersion";
    private static final List<String> CATEGORIES = List.of(
            "Correctness",
            "Optimizations",
            "Tuning",
            "Features",
            "Recovery",
            "Compatibility",
            "Diagnostics",
            "Experimental");

    private static final String SUPPORTED_SERVER_VERSION = "0\\.7\\.\\d+";

    private record MixinToggle(
            String[] jsonPath,
            boolean defaultEnabled,
            List<String> mixins,
            boolean runtimeDependency,
            List<String> uses) {}

    private static final List<MixinToggle> TOGGLES = List.of(
            toggle("Optimizations", "CollisionConfig", true, "MixinCollisionConfig"),
            toggle("Optimizations", "KDTree", false, "MixinKDTree"),
            toggle("Optimizations", "SpawnManagerRecalc", true, "MixinWorldSpawnManager", "MixinWorldSpawnLifecycle"),
            toggle("Optimizations", "RepulsionTicker", true, "MixinRepulsionTicker"),
            toggle("Optimizations", "SkipEmptyLightSections", true, "MixinFloodLightCalculation"),
            toggle("Optimizations", "RocksDbCompression", false, "MixinRocksDbCompression"),
            toggle(
                    "Optimizations",
                    "FluidDiscovery",
                    true,
                    "MixinFluidPluginDiscovery",
                    "MixinFluidSectionPaletteAccess"),
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
                    true,
                    "MixinPacketHandlerAssetParts",
                    "MixinCachedPacketAccess"),
            toggle("Diagnostics", "SkipSystemMetrics", false, "MixinStoreSystemMetrics"),
            toggle("Diagnostics", "HytaleServer", true, "MixinHytaleServer"),
            toggle("Diagnostics", "SpawnMarkerBlockStateHeartbeat", true, "MixinSpawnMarkerBlockStateHeartbeat"),
            toggle("Tuning", "CylinderVisibility", false, "MixinCollectVisible"),
            toggle("Tuning", "ChunkStreaming", false, "MixinPlayerChunkTrackerSystems", "MixinPlayerViewRadius"),
            toggle("Tuning", "WorldHeightBounds", false, "MixinChunkTrackerHeightBounds", "MixinWorldHeightBounds"),
            toggle("Tuning", "TickWait", false, "MixinTickSleep"),
            toggle("Tuning", "PathfindingLimits", false, "MixinAStarLimits"),
            toggle("Features", "OriginRetention", false, "MixinChunkUnloadingSystem"),
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
            toggle("Recovery", "InvalidSavedHolders", false, "MixinEntityChunkLoadingSystem"),
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
                    "MixinNettyChannelConnection"),
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
            toggle("Correctness", "WorldMapPacing", true, "MixinWorldMapPacing"),
            toggle("Recovery", "InteractionChain", false, "MixinInteractionChain"),
            toggle("Correctness", "MarkerAddRemoveSystem", true, "MixinMarkerAddRemoveSystem"),
            toggle("Correctness", "TickingSpawnMarkerSystem", true, "MixinTickingSpawnMarkerSystem"),
            toggle("Recovery", "WorldMapTracker", false, "MixinWorldMapTracker"),
            toggle("Correctness", "WorldMapCache", true, "MixinWorldMapManager"),
            toggle("Recovery", "WorldSpawningSystem", false, "MixinWorldSpawningSystem"),
            toggle("Recovery", "DuplicateComponentReferences", false, "MixinBlockComponentChunk"),
            toggle("Correctness", "TrackedPlacementOnAddRemove", true, "MixinTrackedPlacementOnAddRemove"),
            toggle("Compatibility", "DisableLiveConfig", false, "MixinLiveConfigModule"),
            toggle("Compatibility", "PageManager", true, "MixinPageManager"),
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
            toggle("Correctness", "WorldNullGuard", true, "MixinWorld"),
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
            toggle("Correctness", "SetMemoriesCapacityInteraction", true, "MixinSetMemoriesCapacityInteraction"),
            toggle("Correctness", "StateSupport", true, "MixinStateSupport"),
            toggle("Correctness", "UUIDSystem", true, "MixinUUIDSystem"),
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
                            "MixinWorldOwnership",
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
                    "MixinRocksDbBlockComponentScanBounds"));

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
        boolean unsupportedServerVersion = serverVersion == null || !serverVersion.matches(SUPPORTED_SERVER_VERSION);
        if (unsupportedServerVersion && !getBoolean(config, ALLOW_UNSUPPORTED_SERVER_VERSION)) {
            EarlyOptions.setMixinSelection(Set.of());
            initialized = true;
            System.out.println("[Refixes] Early mixins disabled for Hytale "
                    + (serverVersion == null ? "unknown" : serverVersion)
                    + ". Refixes supports Hytale 0.7.7. Update Refixes for this server version.");
            return;
        }
        requireFabricMixinSupport(RefixesMixinPlugin.class.getClassLoader());
        JsonObject runtimeConfig = readConfig(RUNTIME_CONFIG_PATH);
        Set<String> currentToggles = new HashSet<>();
        for (MixinToggle toggle : TOGGLES) currentToggles.add(path(toggle));
        List<String> migrated = ConfigMigration.migrateMixins(config, currentToggles);
        List<String> runtimeMigrated = ConfigMigration.migrateRuntime(runtimeConfig);
        for (String line : migrated) System.out.println("[Refixes] " + CONFIG_PATH.getFileName() + ": " + line);
        for (String line : runtimeMigrated)
            System.out.println("[Refixes] " + RUNTIME_CONFIG_PATH.getFileName() + ": " + line);
        try {
            EarlyOptions.initializeOutgoingQueueLimits(runtimeConfig);
        } catch (IllegalStateException failure) {
            throw new IllegalStateException(RUNTIME_CONFIG_PATH + ": " + failure.getMessage(), failure);
        }
        Set<String> required = runtimeDependencies(runtimeConfig);
        Set<String> enabled = new HashSet<>();
        for (MixinToggle toggle : TOGGLES) {
            if (!toggleEnabled(config, toggle)) continue;
            for (String mixin : toggle.mixins) {
                if (!toggle.runtimeDependency || required.contains(mixin)) enabled.add(mixin);
            }
            enabled.addAll(toggle.uses);
        }
        validateDependencies(config, enabled);
        Set<String> selected = Set.copyOf(enabled);
        enabledMixins = selected;
        EarlyOptions.setMixinSelection(selected);
        initialized = true;
        if (unsupportedServerVersion) {
            System.out.println("[Refixes] Version gate overridden for Hytale "
                    + (serverVersion == null ? "unknown" : serverVersion)
                    + ". Applying configured mixins.");
        }
        writeConfig(CONFIG_PATH, serializeMixins(config));
        if (!runtimeMigrated.isEmpty()) writeConfig(RUNTIME_CONFIG_PATH, runtimeConfig);
        System.out.println("[Refixes] === Early mixin patches ===");
        for (String mixin : ALL_MIXINS.stream().sorted().toList())
            System.out.println("[Refixes]   - " + (selected.contains(mixin) ? "[x] " : "[ ] ") + mixin);
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

    private static void validateDependencies(JsonObject config, Set<String> selected) {
        for (String key : List.of("CollectVisible")) {
            if (toggleEnabled(config, "Experimental", "Parallel", key)) {
                requireDependency(
                        selected.contains("MixinParallelTask"),
                        "Mixins.Experimental.Parallel." + key,
                        "Mixins.Correctness.ParallelTaskLifecycle");
            }
        }
        if (selected.contains("MixinCubicTransferShutdown")) {
            requireDependency(
                    selected.contains("MixinSaveCompletionWorld"),
                    "Mixins.Correctness.WorldTransfer",
                    "Mixins.Correctness.StorageOrdering");
        }
        if (selected.contains("MixinWorldRetirement")) {
            requireDependency(
                    selected.contains("MixinUniverseRetirement"),
                    "Mixins.Correctness.WorldRetirement",
                    "Mixins.Correctness.WorldReopenGuard");
        }
        if (selected.contains("MixinQuicheChannel")) {
            requireDependency(
                    selected.contains("MixinSetupStagePacing"),
                    "Mixins.Correctness.OutgoingQueueLimits",
                    "Mixins.Correctness.SetupPacing");
        }
        if (selected.contains("MixinInstancesPlugin")) {
            requireDependency(
                    selected.contains("MixinChunkStoreAccess") && selected.contains("MixinChunkGridAccess"),
                    "Mixins.Features.SharedInstances",
                    "Mixins.Features.SharedInstanceStorage");
        }
    }

    private static void requireDependency(boolean available, String feature, String dependency) {
        if (!available)
            throw new IllegalStateException(feature + " requires " + dependency + " to be enabled in " + CONFIG_PATH);
    }

    private static boolean toggleEnabled(JsonObject config, String category, String group, String key) {
        for (MixinToggle toggle : TOGGLES) {
            String[] path = toggle.jsonPath;
            if (path.length == 4 && path[1].equals(category) && path[2].equals(group) && path[3].equals(key))
                return toggleEnabled(config, toggle);
        }
        throw new IllegalArgumentException("Unknown mixin policy: " + category + "." + group + "." + key);
    }

    private static boolean toggleEnabled(JsonObject config, MixinToggle toggle) {
        Boolean value = getOptionalBoolean(config, toggle.jsonPath);
        return value == null ? toggle.defaultEnabled : value;
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
        config.addProperty(ALLOW_UNSUPPORTED_SERVER_VERSION, getBoolean(config, ALLOW_UNSUPPORTED_SERVER_VERSION));
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
