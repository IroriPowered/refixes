package cc.irori.refixes.early;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

public class RefixesMixinPlugin implements IMixinConfigPlugin {

    private static final Path CONFIG_PATH = Paths.get("mods", "IroriPowered_Refixes", "Refixes.json");
    private static final Path RUNTIME_CONFIG_PATH = CONFIG_PATH.resolveSibling("config.json");
    private static final List<String> CATEGORIES = List.of(
            "Correctness",
            "Optimizations",
            "Tuning",
            "Features",
            "Recovery",
            "Compatibility",
            "Diagnostics",
            "Experimental");

    private record MixinToggle(String[] jsonPath, boolean defaultEnabled, List<String> mixins) {}

    private static final List<MixinToggle> TOGGLES = List.of(
            toggle("Optimizations", "CollisionConfig", true, "MixinCollisionConfig"),
            toggle("Optimizations", "KDTree", true, "MixinKDTree"),
            toggle("Optimizations", "SpawnManagerRecalc", true, "MixinWorldSpawnManager", "MixinWorldSpawnLifecycle"),
            toggle("Optimizations", "RepulsionTicker", true, "MixinRepulsionTicker"),
            toggle("Optimizations", "SkipEmptyLightSections", true, "MixinFloodLightCalculation"),
            toggle(
                    "Optimizations",
                    "FluidDiscovery",
                    true,
                    "MixinFluidPluginDiscovery",
                    "MixinFluidSectionPaletteAccess"),
            toggle("Diagnostics", "SkipSystemMetrics", false, "MixinStoreSystemMetrics"),
            toggle("Diagnostics", "HytaleServer", true, "MixinHytaleServer"),
            toggle("Diagnostics", "SpawnMarkerBlockStateHeartbeat", true, "MixinSpawnMarkerBlockStateHeartbeat"),
            toggle("Tuning", "CylinderVisibility", false, "MixinCollectVisible"),
            toggle("Tuning", "ChunkStreaming", false, "MixinPlayerChunkTrackerSystems", "MixinPlayerViewRadius"),
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
            toggle("Recovery", "TickSurvival", false, "MixinStoreTickGuard"),
            toggle("Recovery", "CorruptSectionReset", false, "MixinBlockSectionSafety"),
            toggle("Recovery", "MotionControllerBase", false, "MixinMotionControllerBase"),
            toggle("Recovery", "TurnOffTeleportersSystem", false, "MixinTurnOffTeleportersSystem"),
            toggle("Recovery", "InvalidSavedHolders", false, "MixinEntityChunkLoadingSystem"),
            toggle("Recovery", "InvalidCollisionData", false, "MixinCollisionModule"),
            toggle("Correctness", "MountPlugin", true, "MixinMountPlugin"),
            toggle("Correctness", "QuicheChannel", true, "MixinQuicheChannel"),
            toggle("Recovery", "InteractionChain", false, "MixinInteractionChain"),
            toggle("Recovery", "MarkerAddRemoveSystem", false, "MixinMarkerAddRemoveSystem"),
            toggle("Recovery", "TickingSpawnMarkerSystem", false, "MixinTickingSpawnMarkerSystem"),
            toggle("Recovery", "WorldMapTracker", false, "MixinWorldMapTracker"),
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
            toggle("Correctness", "WorldNullGuard", true, "MixinWorld"),
            toggle("Correctness", "WorldEventSaving", true, "MixinGlobalWorldEventManager"),
            toggle("Correctness", "WorldTransfer", true, "MixinWorldTransfer", "MixinPlayerTeleport"),
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
            toggle("Correctness", "NPCKillsEntitySystem", true, "MixinNPCKillsEntitySystem"),
            toggle("Correctness", "PortalDeviceSummonPage", true, "MixinPortalDeviceSummonPage"),
            toggle("Correctness", "RemovalSystem", true, "MixinRemovalSystem", "MixinPortalWorldAccessor"),
            toggle("Correctness", "SetMemoriesCapacityInteraction", true, "MixinSetMemoriesCapacityInteraction"),
            toggle("Correctness", "StateSupport", true, "MixinStateSupport"),
            toggle("Correctness", "UUIDSystem", true, "MixinUUIDSystem"),
            toggle("Correctness", "DeployableOwnerComponent", true, "MixinDeployableOwnerComponent"),
            toggle("Correctness", "TeleportToPlayerCommand", true, "MixinTeleportToPlayerCommand"),
            toggle("Experimental", "Parallel", "CollectVisible", false, "MixinCollectVisibleParallel"),
            toggle("Experimental", "Parallel", "SpatialCollection", false, "MixinSpatialSystem"),
            toggle(
                    "Experimental",
                    "PathfindingBudget",
                    false,
                    "MixinBodyMotionFindBase",
                    "MixinWorldPathfindingBudget"));

    private static final Set<String> DEPENDENCY_MIXINS = Set.of(
            "MixinBlockComponentSectionAccess",
            "MixinFluidSection",
            "MixinTickingThreadAccess",
            "MixinNPCFreezeOwnership",
            "MixinWorldPauseOwner");
    private static final Set<String> ALL_MIXINS = allMixins();

    private String mixinPackage;
    private volatile Set<String> enabledMixins = Collections.emptySet();
    private volatile boolean initialized;

    private static MixinToggle toggle(String category, String key, boolean defaultEnabled, String... mixins) {
        return new MixinToggle(new String[] {"Mixins", category, key}, defaultEnabled, List.of(mixins));
    }

    private static MixinToggle toggle(
            String category, String group, String key, boolean defaultEnabled, String... mixins) {
        return new MixinToggle(new String[] {"Mixins", category, group, key}, defaultEnabled, List.of(mixins));
    }

    private static Set<String> allMixins() {
        Set<String> mixins = new HashSet<>(DEPENDENCY_MIXINS);
        for (MixinToggle toggle : TOGGLES) mixins.addAll(toggle.mixins);
        return Set.copyOf(mixins);
    }

    @Override
    public void onLoad(String mixinPackage) {
        initialized = false;
        enabledMixins = Collections.emptySet();
        EarlyOptions.clearMixinSelection();
        requireFabricMixinSupport(RefixesMixinPlugin.class.getClassLoader());
        this.mixinPackage = mixinPackage;
        JsonObject config = readConfig(CONFIG_PATH);
        JsonObject runtimeConfig = readConfig(RUNTIME_CONFIG_PATH);
        Set<String> enabled = new HashSet<>();
        for (MixinToggle toggle : TOGGLES) {
            if (toggleEnabled(config, toggle)) enabled.addAll(toggle.mixins);
        }
        validateDependencies(config, enabled);
        boolean cleaner = getBoolean(runtimeConfig, "Runtime", "Recovery", "UnknownBlockCleaner", "Enabled");
        if (cleaner && getBoolean(runtimeConfig, "Runtime", "Recovery", "UnknownBlockCleaner", "ScanContainers"))
            enabled.add("MixinBlockComponentSectionAccess");
        if (cleaner && getBoolean(runtimeConfig, "Runtime", "Recovery", "UnknownBlockCleaner", "ScanFluids"))
            enabled.add("MixinFluidSection");
        if (getBoolean(runtimeConfig, "Runtime", "Recovery", "Watchdog", "AutoRestartWorlds"))
            enabled.add("MixinTickingThreadAccess");
        if (getBoolean(runtimeConfig, "Runtime", "Tuning", "AiTickThrottler", "Enabled"))
            enabled.add("MixinNPCFreezeOwnership");
        if (getBoolean(runtimeConfig, "Runtime", "Tuning", "IdleWorldPause", "Enabled"))
            enabled.add("MixinWorldPauseOwner");
        Set<String> selected = Set.copyOf(enabled);
        writeConfig(config);
        enabledMixins = selected;
        EarlyOptions.setMixinSelection(selected);
        initialized = true;
        System.out.println("[Refixes] === Early mixin patches ===");
        for (String mixin : ALL_MIXINS.stream().sorted().toList())
            System.out.println("[Refixes]   - " + (selected.contains(mixin) ? "[x] " : "[ ] ") + mixin);
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

    private static void writeConfig(JsonObject config) {
        try {
            JsonObject mixins = new JsonObject();
            for (String category : CATEGORIES) mixins.add(category, new JsonObject());
            for (MixinToggle toggle : TOGGLES)
                setBoolean(
                        mixins,
                        java.util.Arrays.copyOfRange(toggle.jsonPath, 1, toggle.jsonPath.length),
                        toggleEnabled(config, toggle));
            config.add("Mixins", mixins);
            Files.createDirectories(CONFIG_PATH.getParent());
            Path temporary = Files.createTempFile(CONFIG_PATH.getParent(), "Refixes", ".json.tmp");
            try {
                try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                    new GsonBuilder().setPrettyPrinting().create().toJson(config, writer);
                }
                Files.move(temporary, CONFIG_PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (AtomicMoveNotSupportedException failure) {
            throw new IllegalStateException("Atomic config replacement is unsupported for " + CONFIG_PATH, failure);
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "Failed to write Refixes config at " + CONFIG_PATH + ": " + failure.getMessage(), failure);
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
