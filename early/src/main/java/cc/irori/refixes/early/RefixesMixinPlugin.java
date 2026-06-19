package cc.irori.refixes.early;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class RefixesMixinPlugin implements IMixinConfigPlugin {

    private static final Path CONFIG_PATH = Paths.get("mods", "IroriPowered_Refixes", "Refixes.json");

    private record MixinToggle(String[] jsonPath, boolean enabledWhen, boolean defaultEnabled, List<String> mixins) {
        MixinToggle(String[] jsonPath, boolean enabledWhen, List<String> mixins) {
            this(jsonPath, enabledWhen, true, mixins);
        }
    }

    private static final List<MixinToggle> TOGGLES = List.of(
            new MixinToggle(new String[] {"Mixins", "Optimizations", "FluidPlugin"}, true, List.of("MixinFluidPlugin")),
            new MixinToggle(new String[] {"Mixins", "Optimizations", "BlockModule"}, true, List.of("MixinBlockModule")),
            new MixinToggle(
                    new String[] {"Mixins", "Optimizations", "CollectVisible"}, true, List.of("MixinCollectVisible")),
            new MixinToggle(
                    new String[] {"Mixins", "Optimizations", "CollisionConfig"}, true, List.of("MixinCollisionConfig")),
            new MixinToggle(
                    new String[] {"Mixins", "Optimizations", "CollisionAirFastPath"},
                    true,
                    List.of("MixinCollisionAirFastPath")),
            new MixinToggle(
                    new String[] {"Mixins", "Optimizations", "SkipSystemMetrics"},
                    true,
                    false,
                    List.of("MixinStoreSystemMetrics")),
            new MixinToggle(new String[] {"Mixins", "Optimizations", "KDTree"}, true, List.of("MixinKDTree")),
            new MixinToggle(
                    new String[] {"Mixins", "Optimizations", "ChunkSavingSystems"},
                    true,
                    List.of("MixinChunkSavingSystems")),
            new MixinToggle(
                    new String[] {"Mixins", "Optimizations", "ChunkUnloadingSystem"},
                    true,
                    List.of(
                            "MixinChunkUnloadingSystem",
                            "MixinChunkUnloadingSystem$ChunkTrackerAccessor",
                            "MixinChunkUnloadingSystem$DataAccessor")),
            new MixinToggle(
                    new String[] {"Mixins", "Optimizations", "PlayerChunkTrackerSystems"},
                    true,
                    List.of("MixinPlayerChunkTrackerSystems")),
            new MixinToggle(
                    new String[] {"Mixins", "Optimizations", "SpawnManagerRecalc"},
                    true,
                    List.of("MixinWorldSpawnManager")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "ParallelEntityTicking"},
                    true,
                    false,
                    List.of("MixinEntityTickingSystem", "MixinSteeringSystem", "MixinStore")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "ParallelSpatialCollection"},
                    true,
                    false,
                    List.of("MixinSpatialSystem")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "ShutdownSaveTimeout"},
                    true,
                    false,
                    List.of("MixinStoreShutdown")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "PathfindingBudget"},
                    true,
                    false,
                    List.of("MixinBodyMotionFindBase")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "BlockSectionCache"},
                    true,
                    false,
                    List.of("MixinBlockChunk")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "SkipEmptyLightSections"},
                    true,
                    false,
                    List.of("MixinFloodLightCalculation")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "StatRecalcThrottle"},
                    true,
                    false,
                    List.of("MixinStatModifiersManager")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "SharedInstances"},
                    true,
                    false,
                    List.of("MixinInstancesPlugin")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "FluidReplicateChanges"},
                    true,
                    false,
                    List.of("MixinFluidReplicateChanges")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "ChunkReplicateChanges"},
                    true,
                    false,
                    List.of("MixinChunkReplicateChanges")),
            new MixinToggle(
                    new String[] {"Mixins", "Experimental", "ConnectionBackpressure"},
                    true,
                    false,
                    List.of("MixinNettyUtilBackpressure")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "BlockSectionSafety"},
                    true,
                    List.of("MixinBlockSectionSafety")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "MotionControllerBase"},
                    true,
                    List.of("MixinMotionControllerBase")),
            new MixinToggle(new String[] {"Mixins", "Crashfixes", "Player"}, true, List.of("MixinPlayer")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "TurnOffTeleportersSystem"},
                    true,
                    List.of("MixinTurnOffTeleportersSystem")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "EntityChunkLoadingSystem"},
                    true,
                    List.of("MixinEntityChunkLoadingSystem")),
            new MixinToggle(new String[] {"Mixins", "Crashfixes", "AStarBase"}, true, List.of("MixinAStarBase")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "RepulsionTicker"}, true, List.of("MixinRepulsionTicker")),
            new MixinToggle(new String[] {"Mixins", "Crashfixes", "UpdateModule"}, true, List.of("MixinUpdateModule")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "CollisionModule"}, true, List.of("MixinCollisionModule")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "HideEntitySystems"},
                    true,
                    List.of("MixinHideEntitySystems")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "TriggerVolumesPlugin"},
                    true,
                    List.of("MixinTriggerVolumesPlugin")),
            new MixinToggle(new String[] {"Mixins", "Crashfixes", "VoiceModule"}, true, List.of("MixinVoiceModule")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "ChunkLightDataSerializeSafety"},
                    true,
                    List.of("MixinChunkLightDataSerializeSafety")),
            new MixinToggle(new String[] {"Mixins", "Crashfixes", "PageManager"}, true, List.of("MixinPageManager")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "TeleportToPlayerCommand"},
                    true,
                    List.of("MixinTeleportToPlayerCommand")),
            new MixinToggle(
                    new String[] {"Mixins", "Crashfixes", "DeployableOwnerComponent"},
                    true,
                    List.of("MixinDeployableOwnerComponent")),
            new MixinToggle(new String[] {"Mixins", "Crashfixes", "MountPlugin"}, true, List.of("MixinMountPlugin")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "ArchetypeChunk"}, true, List.of("MixinArchetypeChunk")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "BeaconAddRemoveSystem"},
                    true,
                    List.of("MixinBeaconAddRemoveSystem")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "BlockComponentChunk"},
                    true,
                    List.of("MixinBlockComponentChunk")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "BlockHealthSystem"}, true, List.of("MixinBlockHealthSystem")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "CommandBuffer"}, true, List.of("MixinCommandBuffer")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "CraftingManagerAccessor"},
                    true,
                    List.of("MixinCraftingManagerAccessor")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "EntityViewer"}, true, List.of("MixinEntityViewer")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "GamePacketHandler"}, true, List.of("MixinGamePacketHandler")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "HytaleServer"}, true, List.of("MixinHytaleServer")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "InteractionChain"}, true, List.of("MixinInteractionChain")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "MarkerAddRemoveSystem"},
                    true,
                    List.of("MixinMarkerAddRemoveSystem")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "NPCKillsEntitySystem"},
                    true,
                    List.of("MixinNPCKillsEntitySystem")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "Options"}, true, List.of("MixinOptions")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "PlayerViewRadius"}, true, List.of("MixinPlayerViewRadius")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "PortalDeviceSummonPage"},
                    true,
                    List.of("MixinPortalDeviceSummonPage")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "PortalWorldAccessor"},
                    true,
                    List.of("MixinPortalWorldAccessor")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "PrefabListExtraRoots"},
                    true,
                    List.of("MixinAssetPrefabFileProvider", "MixinPrefabPage")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "RemovalSystem"}, true, List.of("MixinRemovalSystem")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "ServerAuthManager"}, true, List.of("MixinServerAuthManager")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "SetMemoriesCapacityInteraction"},
                    true,
                    List.of("MixinSetMemoriesCapacityInteraction")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "SpawnMarkerBlockStateHeartbeat"},
                    true,
                    List.of("MixinSpawnMarkerBlockStateHeartbeat")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "StateSupport"}, true, List.of("MixinStateSupport")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "TickingSpawnMarkerSystem"},
                    true,
                    List.of("MixinTickingSpawnMarkerSystem")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "TickingThread"}, true, List.of("MixinTickingThread")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "TickingThreadAssert"},
                    true,
                    List.of("MixinTickingThreadAssert")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "TrackedPlacementAccessor"},
                    true,
                    List.of("MixinTrackedPlacementAccessor")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "TrackedPlacementOnAddRemove"},
                    true,
                    List.of("MixinTrackedPlacementOnAddRemove")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "UUIDSystem"}, true, List.of("MixinUUIDSystem")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "UpdateCheckCommand"}, true, List.of("MixinUpdateCheckCommand")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "UpdateDownloadCommand"},
                    true,
                    List.of("MixinUpdateDownloadCommand")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "WorldPauseCommand"}, true, List.of("MixinWorldPauseCommand")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "World"}, true, List.of("MixinWorld")),
            new MixinToggle(new String[] {"Mixins", "Helpers", "WorldConfig"}, true, List.of("MixinWorldConfig")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "WorldMapTracker"}, true, List.of("MixinWorldMapTracker")),
            new MixinToggle(
                    new String[] {"Mixins", "Helpers", "WorldSpawningSystem"},
                    true,
                    List.of("MixinWorldSpawningSystem")),
            new MixinToggle(
                    new String[] {"HypixelServices", "LiveConfig"}, true, false, List.of("MixinLiveConfigModule")));

    private String mixinPackage;
    private Set<String> disabledMixins = Collections.emptySet();

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage;

        JsonObject config = readConfig();
        if (config == null) {
            config = new JsonObject();
        }

        Set<String> disabled = new HashSet<>();
        for (MixinToggle toggle : TOGGLES) {
            Boolean value = getBoolean(config, toggle.jsonPath);
            boolean enabled = value == null ? toggle.defaultEnabled : (value == toggle.enabledWhen);
            if (!enabled) {
                disabled.addAll(toggle.mixins);
            }
            setBoolean(config, toggle.jsonPath, enabled == toggle.enabledWhen);
        }

        disabledMixins = disabled;
        writeConfig(config);

        System.out.println("[Refixes] === Early mixin patches ===");
        for (MixinToggle toggle : TOGGLES) {
            for (String mixin : toggle.mixins) {
                String marker = disabled.contains(mixin) ? "[ ]" : "[x]";
                System.out.println("[Refixes]   - " + marker + " " + mixin);
            }
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        String simpleName = mixinClassName;
        if (mixinPackage != null && mixinClassName.startsWith(mixinPackage + ".")) {
            simpleName = mixinClassName.substring(mixinPackage.length() + 1);
        }
        return !disabledMixins.contains(simpleName);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    private static JsonObject readConfig() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
            JsonElement el = JsonParser.parseReader(reader);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            System.out.println("[Refixes] Failed to read config: " + e.getMessage());
            return null;
        }
    }

    private static void writeConfig(JsonObject config) {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH, StandardCharsets.UTF_8)) {
                gson.toJson(config, writer);
            }
        } catch (Exception e) {
            System.out.println("[Refixes] Failed to write config: " + e.getMessage());
        }
    }

    private static Boolean getBoolean(JsonObject root, String[] path) {
        JsonObject current = root;
        for (int i = 0; i < path.length - 1; i++) {
            JsonElement el = current.get(path[i]);
            if (el == null || !el.isJsonObject()) {
                return null;
            }
            current = el.getAsJsonObject();
        }
        JsonElement leaf = current.get(path[path.length - 1]);
        if (leaf != null && leaf.isJsonPrimitive() && leaf.getAsJsonPrimitive().isBoolean()) {
            return leaf.getAsBoolean();
        }
        return null;
    }

    private static void setBoolean(JsonObject root, String[] path, boolean value) {
        JsonObject current = root;
        for (int i = 0; i < path.length - 1; i++) {
            JsonElement el = current.get(path[i]);
            if (el == null || !el.isJsonObject()) {
                JsonObject created = new JsonObject();
                current.add(path[i], created);
                current = created;
            } else {
                current = el.getAsJsonObject();
            }
        }
        current.addProperty(path[path.length - 1], value);
    }
}
