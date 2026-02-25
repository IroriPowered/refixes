package cc.irori.refixes.early;

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RefixesMixinPlugin implements IMixinConfigPlugin {

    private static final Path CONFIG_PATH = Paths.get("mods", "IroriPowered_Refixes", "Refixes.json");

    private record MixinToggle(String[] jsonPath, boolean enabledWhen, boolean defaultEnabled, List<String> mixins) {
        MixinToggle(String[] jsonPath, boolean enabledWhen, List<String> mixins) {
            this(jsonPath, enabledWhen, true, mixins);
        }
    }

    private static final List<MixinToggle> TOGGLES = List.of(
            // Fluid processing
            new MixinToggle(new String[] {"Mixins", "MixinFluidPlugin"}, true, List.of("MixinFluidPlugin")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinFluidReplicateChanges"},
                    true,
                    false,
                    List.of("MixinFluidReplicateChanges")),

            // Block processing
            new MixinToggle(new String[] {"Mixins", "MixinBlockModule"}, true, List.of("MixinBlockModule")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinBlockComponentChunk"}, true, List.of("MixinBlockComponentChunk")),
            new MixinToggle(new String[] {"Mixins", "MixinBlockHealthSystem"}, true, List.of("MixinBlockHealthSystem")),

            // Chunk systems
            new MixinToggle(
                    new String[] {"Mixins", "MixinChunkReplicateChanges"},
                    true,
                    false,
                    List.of("MixinChunkReplicateChanges")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinChunkSavingSystems"}, true, List.of("MixinChunkSavingSystems")),

            // Entity systems
            new MixinToggle(new String[] {"Mixins", "MixinArchetypeChunk"}, true, List.of("MixinArchetypeChunk")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinEntityTickingSystem"},
                    true,
                    false,
                    List.of("MixinEntityTickingSystem")),
            new MixinToggle(new String[] {"Mixins", "MixinStore"}, true, false, List.of("MixinStore")),
            new MixinToggle(new String[] {"Mixins", "MixinCommandBuffer"}, true, List.of("MixinCommandBuffer")),
            new MixinToggle(new String[] {"Mixins", "MixinRemovalSystem"}, true, List.of("MixinRemovalSystem")),
            new MixinToggle(new String[] {"Mixins", "MixinUUIDSystem"}, true, List.of("MixinUUIDSystem")),
            new MixinToggle(new String[] {"Mixins", "MixinCollectVisible"}, true, List.of("MixinCollectVisible")),
            new MixinToggle(new String[] {"Mixins", "MixinKDTree"}, true, List.of("MixinKDTree")),

            // Ticking
            new MixinToggle(new String[] {"Mixins", "MixinTickingThread"}, true, List.of("MixinTickingThread")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinTickingThreadAssert"}, true, List.of("MixinTickingThreadAssert")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinTickingSpawnMarkerSystem"},
                    true,
                    List.of("MixinTickingSpawnMarkerSystem")),

            // Player & network
            new MixinToggle(
                    new String[] {"Mixins", "MixinPlayerChunkTrackerSystems"},
                    true,
                    List.of("MixinPlayerChunkTrackerSystems")),
            new MixinToggle(new String[] {"Mixins", "MixinGamePacketHandler"}, true, List.of("MixinGamePacketHandler")),

            // Interactions
            new MixinToggle(new String[] {"Mixins", "MixinInteractionChain"}, true, List.of("MixinInteractionChain")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinInteractionManager"}, true, List.of("MixinInteractionManager")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinSetMemoriesCapacityInteraction"},
                    true,
                    List.of("MixinSetMemoriesCapacityInteraction")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinCraftingManagerAccessor"},
                    true,
                    List.of("MixinCraftingManagerAccessor")),

            // World & universe
            new MixinToggle(new String[] {"Mixins", "MixinWorld"}, true, List.of("MixinWorld")),
            new MixinToggle(new String[] {"Mixins", "MixinWorldConfig"}, true, List.of("MixinWorldConfig")),
            new MixinToggle(new String[] {"Mixins", "MixinWorldMapTracker"}, true, List.of("MixinWorldMapTracker")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinWorldSpawningSystem"}, true, List.of("MixinWorldSpawningSystem")),
            new MixinToggle(new String[] {"Mixins", "MixinUniverse"}, true, List.of("MixinUniverse")),
            new MixinToggle(new String[] {"Mixins", "MixinPrefabLoader"}, true, List.of("MixinPrefabLoader")),
            new MixinToggle(new String[] {"Mixins", "MixinInstancesPlugin"}, true, List.of("MixinInstancesPlugin")),

            // Portals
            new MixinToggle(
                    new String[] {"Mixins", "MixinPortalDeviceSummonPage"},
                    true,
                    List.of("MixinPortalDeviceSummonPage")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinPortalWorldAccessor"}, true, List.of("MixinPortalWorldAccessor")),

            // Placement & markers
            new MixinToggle(
                    new String[] {"Mixins", "MixinTrackedPlacementAccessor"},
                    true,
                    List.of("MixinTrackedPlacementAccessor")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinTrackedPlacementOnAddRemove"},
                    true,
                    List.of("MixinTrackedPlacementOnAddRemove")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinBeaconAddRemoveSystem"}, true, List.of("MixinBeaconAddRemoveSystem")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinMarkerAddRemoveSystem"}, true, List.of("MixinMarkerAddRemoveSystem")),

            // Server
            new MixinToggle(new String[] {"Mixins", "MixinHytaleServer"}, true, List.of("MixinHytaleServer")),
            new MixinToggle(
                    new String[] {"Mixins", "MixinHytaleServerConfig"},
                    true,
                    false,
                    List.of("MixinHytaleServerConfig")));

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
        JsonObject mixinsSection = new JsonObject();
        for (MixinToggle toggle : TOGGLES) {
            Boolean value = getBoolean(config, toggle.jsonPath);
            boolean enabled;
            if (value == null) {
                enabled = toggle.defaultEnabled;
            } else {
                enabled = (value == toggle.enabledWhen);
            }
            if (!enabled) {
                disabled.addAll(toggle.mixins);
            }
            String leafKey = toggle.jsonPath[toggle.jsonPath.length - 1];
            mixinsSection.addProperty(leafKey, enabled == toggle.enabledWhen);
        }

        disabledMixins = disabled;

        // Write resolved mixin toggles back to config
        config.add("Mixins", mixinsSection);
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
}
