package local.refixes.policy;

import static local.hyinit.verify.RegressionRunner.require;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class PolicyMixinConfigRegression {

    private static final String PLUGIN = "cc.irori.refixes.early.RefixesMixinPlugin";
    private static final String OPTIONS = "cc.irori.refixes.early.EarlyOptions";
    private static final String CONFIG = "mods/IroriPowered_Refixes/Refixes.json";
    private static final String MIXIN_PACKAGE = "cc.irori.refixes.early.mixin";
    private static final String SERVER_VERSION = "refixes.policy.serverVersion";

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            runChild(args[0]);
            return;
        }
        runInWorkingDirectory("policy");
        runInWorkingDirectory("malformed");
        runInWorkingDirectory("missing");
        runInWorkingDirectory("mapping");
        runInWorkingDirectory("explicit");
        runInWorkingDirectory("parallel-dependency");
        runInWorkingDirectory("storage-dependency");
        runInWorkingDirectory("transfer-dependency");
        runInWorkingDirectory("misplaced-runtime");
        runInWorkingDirectory("reload");
        runInWorkingDirectory("failed-write");
        runInWorkingDirectory("all-disabled");
        runInWorkingDirectory("version-accepted", "0.7.7");
        runInWorkingDirectory("version-older", "0.7.6");
        runInWorkingDirectory("version-older-allowed-unchanged", "0.7.5");
        runInWorkingDirectory("version-older-legacy-false", "0.7.5");
        runInWorkingDirectory("version-accepted", "0.8.0-pre.6");
        runInWorkingDirectory("version-accepted", "0.8.0-pre.6.1");
        runInWorkingDirectory("version-newer-minor", "0.9.0");
        runInWorkingDirectory("version-unreadable", "0.7.7.1");
        runInWorkingDirectory("version-unreadable", "0.8.0.1");
        runInWorkingDirectory("version-newer-minor", "0.9.0-pre.1");
        runInWorkingDirectory("version-unreadable-all", "0.8.1-pre.1");
        runInWorkingDirectory("version-unreadable-all", "0.7.7.1");
        runInWorkingDirectory("version-never", "0.7.8");
        runInWorkingDirectory("version-never-lowercase", "0.7.8");
        runInWorkingDirectory("version-invalid-logged", "0.9.0");
        runInWorkingDirectory("version-override-new-key", "0.7.5");
        runInWorkingDirectory("version-override-dependency", "0.7.8");
        runInWorkingDirectory("version-unchanged-unavailable", "0.7.8");
        runInWorkingDirectory("version-unchanged-unavailable-all", "0.9.0");
        Path shared = Files.createTempDirectory("refixes-policy-round-trip-");
        runInWorkingDirectory(shared, "round-trip-first", "0.7.7");
        runInWorkingDirectory(shared, "round-trip-other", "0.8.0-pre.6");
        runInWorkingDirectory(shared, "round-trip-back", "0.7.7");
        System.out.println("PASS early mixin policy configuration regressions");
    }

    private static void runInWorkingDirectory(String scenario) throws Exception {
        runInWorkingDirectory(scenario, null);
    }

    private static void runInWorkingDirectory(String scenario, String serverVersion) throws Exception {
        runInWorkingDirectory(Files.createTempDirectory("refixes-policy-"), scenario, serverVersion);
    }

    private static void runInWorkingDirectory(Path workingDirectory, String scenario, String serverVersion)
            throws Exception {
        String classPath = absoluteClassPath();
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString()));
        if (serverVersion != null) {
            classPath = serverStub(workingDirectory, serverVersion) + System.getProperty("path.separator") + classPath;
            command.add("-D" + SERVER_VERSION + "=" + serverVersion);
        }
        if (System.getProperty("refixes.docs") != null) command.add("-Drefixes.docs=" + System.getProperty("refixes.docs"));
        command.addAll(List.of("-cp", classPath, PolicyMixinConfigRegression.class.getName(), scenario));
        Process process = new ProcessBuilder(command)
                .directory(workingDirectory.toFile())
                .inheritIO()
                .start();
        int status = process.waitFor();
        if (status != 0) {
            throw new AssertionError("Policy scenario failed: " + scenario
                    + (serverVersion == null ? "" : " on " + serverVersion) + " (exit " + status + ")");
        }
    }

    private static Path serverStub(Path directory, String version) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        Path jar = directory.resolve("server-version-" + version + ".jar");
        try (InputStream main = PolicyMixinConfigRegression.class.getResourceAsStream("/com/hypixel/hytale/Main.class");
                JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            require(main != null, "Hytale server must be on the regression classpath");
            output.putNextEntry(new JarEntry("com/hypixel/hytale/Main.class"));
            main.transferTo(output);
            output.closeEntry();
        }
        return jar;
    }

    private static String absoluteClassPath() {
        return String.join(
                System.getProperty("path.separator"),
                Arrays.stream(System.getProperty("java.class.path").split(System.getProperty("path.separator")))
                        .map(entry -> Path.of(entry).toAbsolutePath().normalize().toString())
                        .toList());
    }

    private static void runChild(String scenario) throws Exception {
        Path config = Path.of(CONFIG);
        if (!scenario.equals("missing")) Files.createDirectories(config.getParent());
        switch (scenario) {
            case "malformed" -> verifyMalformedConfig(config);
            case "missing" -> verifyMissingConfig(config);
            case "policy" -> verifyPolicyAndSerialization(config);
            case "mapping" -> verifyMapping();
            case "explicit" -> verifyExplicitSelection(config);
            case "parallel-dependency" -> verifyDependency(config,
                    "{\"Mixins\":{\"Correctness\":{\"ParallelTaskLifecycle\":false},\"Experimental\":{\"Parallel\":{\"CollectVisible\":true}}}}",
                    "Mixins.Experimental.Parallel.CollectVisible", "Mixins.Correctness.ParallelTaskLifecycle", "MixinCollectVisibleParallel");
            case "storage-dependency" -> verifyDependency(config,
                    "{\"Mixins\":{\"Features\":{\"SharedInstances\":true,\"SharedInstanceStorage\":false}}}",
                    "Mixins.Features.SharedInstances", "Mixins.Features.SharedInstanceStorage", "MixinInstancesPlugin");
            case "transfer-dependency" -> verifyTransferDependency(config);
            case "round-trip-first" -> verifyRoundTrip(config, true);
            case "round-trip-other" -> verifyRoundTripOther(config);
            case "round-trip-back" -> verifyRoundTrip(config, false);
            case "misplaced-runtime" -> verifyMisplacedRuntime(config);
            case "reload" -> verifyReload(config);
            case "failed-write" -> verifyFailedWrite(config);
            case "all-disabled" -> verifyAllDisabled(config);
            case "version-accepted" -> verifyVersionAccepted(config);
            case "version-older" -> verifyVersionRejected(config, "{}",
                    "is older than 0.7.7, the version this Refixes build was made for. Early patches apply on older"
                            + " versions only when OtherServerVersions is AllPatches.");
            case "version-older-allowed-unchanged" -> verifyVersionRejected(config,
                    "{\"OtherServerVersions\":\"AllNewerVersions\"}",
                    "Early patches apply on older versions only when OtherServerVersions is AllPatches.");
            case "version-older-legacy-false" -> verifyVersionRejected(config,
                    "{\"AllowUnsupportedServerVersion\":false}",
                    "Refixes.json: Ignored unknown settings that stay in the file: AllowUnsupportedServerVersion");
            case "version-newer-minor" -> verifyVersionRejected(config, "{}",
                    "On versions after Hytale 0.8 early patches apply only when OtherServerVersions is"
                            + " AllNewerVersions or AllPatches.");
            case "version-unreadable" -> verifyVersionRejected(config, "{}",
                    "Refixes cannot compare the Hytale version " + serverVersion() + " with ");
            case "version-unreadable-all" -> verifyVersionRejected(config,
                    "{\"OtherServerVersions\":\"AllPatches\"}",
                    "Refixes cannot compare the Hytale version " + serverVersion() + " with ");
            case "version-never" -> verifyVersionRejected(config,
                    "{\"OtherServerVersions\":\"Never\"}",
                    "OtherServerVersions is Never.");
            case "version-never-lowercase" -> verifyVersionRejected(config,
                    "{\"OtherServerVersions\":\"never\"}",
                    "OtherServerVersions is Never.");
            case "version-invalid-logged" -> verifyVersionRejected(config,
                    "{\"OtherServerVersions\":\"AllNewerVersion\"}",
                    "Refixes.json: Replaced invalid value OtherServerVersions=\"AllNewerVersion\" with the default");
            case "version-override-new-key" -> verifyVersionOverride(config,
                    "{\"OtherServerVersions\":\"allpatches\",\"Mixins\":{\"Recovery\":{\"ShutdownSaveTimeout\":true}}}",
                    "Version gate overridden for Hytale 0.7.5 by OtherServerVersions=AllPatches");
            case "version-override-dependency" -> verifyVersionOverrideDependency(config);
            case "version-unchanged-unavailable" -> verifyVersionUnchangedUnavailable(config, null);
            case "version-unchanged-unavailable-all" -> verifyVersionUnchangedUnavailable(config, "AllNewerVersions");
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        }
    }

    private static void verifyPolicyAndSerialization(Path config) throws Exception {
        String original = """
                {
                  "Mixins": {
                    "Tuning": {"CylinderVisibility": false},
                    "Recovery": {"ShutdownSaveTimeout": true},
                    "Compatibility": {"DisableLiveConfig": true},
                    "Experimental": {"Parallel": {"CollectVisible": true}}
                  },
                  "Early": {"PathfindingMaxPathLength": 321},
                  "Runtime": {"Recovery": {"UnknownBlockCleaner": {"Enabled": true, "ScanContainers": true, "ScanFluids": false}}},
                  "Other": {"Keep": "unchanged"}
                }
                """;
        Files.writeString(config, original, StandardCharsets.UTF_8);
        Files.writeString(config.resolveSibling("config.json"), """
                {"Runtime":{"Recovery":{"UnknownBlockCleaner":{"Enabled":true,"ScanContainers":true,"ScanFluids":false}}}}
                """);

        Class<?> options = Class.forName(OPTIONS);
        Method isMixinEnabled = options.getMethod("isMixinEnabled", String.class);
        require(!enabled(isMixinEnabled, "MixinMountPlugin"), "mixin lookup must be false before bootstrap");
        Method hasSelection = options.getMethod("hasMixinSelection");
        require(!(boolean) hasSelection.invoke(null), "selection must be absent before bootstrap");

        try (URLClassLoader isolated = new URLClassLoader(
                new URL[] {classLocation(options)}, ClassLoader.getPlatformClassLoader())) {
            Class<?> isolatedOptions = isolated.loadClass(OPTIONS);
            Method isolatedLookup = isolatedOptions.getMethod("isMixinEnabled", String.class);
            require(!enabled(isolatedLookup, "MixinMountPlugin"), "isolated mixin lookup must be false before bootstrap");

            Class<?> pluginType = Class.forName(PLUGIN);
            Object plugin = pluginType.getConstructor().newInstance();
            Method preApply = pluginType.getMethod("shouldApplyMixin", String.class, String.class);
            require(!applies(plugin, preApply, "MixinMountPlugin"), "uninitialized plugin must select no mixins");
            pluginType.getMethod("onLoad", String.class).invoke(plugin, MIXIN_PACKAGE);
            Method shouldApply = pluginType.getMethod("shouldApplyMixin", String.class, String.class);

            require(applies(plugin, shouldApply, "MixinStoreShutdown"), "the shutdown timeout switch must select its store mixin");
            require(applies(plugin, shouldApply, "MixinWorldShutdown"), "the shutdown timeout switch must select the world shutdown mixin");
            require(applies(plugin, shouldApply, "MixinLiveConfigModule"), "the live config switch must select its mixin");
            require(applies(plugin, shouldApply, "MixinCollectVisibleParallel"), "current parallel CollectVisible setting must enable its feature");
            require(applies(plugin, shouldApply, "MixinBlockComponentSectionAccess"), "current container cleaner dependency must select its accessor");
            require(!applies(plugin, shouldApply, "MixinFluidSection"), "disabled fluid cleaning must not select its accessor");
            require(applies(plugin, shouldApply, "MixinChunkGridAccess"), "shared storage access must be independent of instance creation");
            require(enabled(isMixinEnabled, "MixinMountPlugin"), "bootstrap must publish selected mixins before lazy targets load");
            require((boolean) hasSelection.invoke(null), "successful bootstrap must publish a selection");
            require(!enabled(isMixinEnabled, null), "null mixin lookup must be false");
            require(!enabled(isMixinEnabled, "MixinNotConfigured"), "unknown mixin lookup must be false");
            require(!applies(plugin, shouldApply, "MixinNotConfigured"), "unknown mixins must never apply");
            require(enabled(isMixinEnabled, "MixinBlockComponentSectionAccess"), "bootstrap must publish dependency mixins");
            require(!enabled(isMixinEnabled, "MixinCollectVisible"), "disabled mixins must not be published as active");
            require(enabled(isolatedLookup, "MixinMountPlugin"), "selected mixins must cross the early/main classloader boundary");
            require(!enabled(isolatedLookup, "MixinCollectVisible"), "isolated lookup must preserve disabled mixin state");

            JsonObject root = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
            JsonObject mixins = root.getAsJsonObject("Mixins");
            require(mixins.getAsJsonObject("Experimental").getAsJsonObject("Parallel").get("CollectVisible").getAsBoolean(), "parallel key missing");
            require(mixins.getAsJsonObject("Tuning").get("PathfindingLimits").getAsJsonPrimitive().isBoolean(), "pathfinding limit key missing");
            require(mixins.getAsJsonObject("Optimizations").has("SkipEmptyLightSections") == hytale08(),
                    "the light toggle must be serialized only on Hytale 0.8");
            require(root.getAsJsonObject("Early").get("PathfindingMaxPathLength").getAsInt() == 321, "Early root section was not preserved");
            require(root.getAsJsonObject("Runtime").getAsJsonObject("Recovery").getAsJsonObject("UnknownBlockCleaner").get("ScanContainers").getAsBoolean(), "Runtime root section was not preserved");
            require(root.getAsJsonObject("Other").get("Keep").getAsString().equals("unchanged"), "unrelated root section was not preserved");
        }
    }

    private static void verifyMalformedConfig(Path config) throws Exception {
        String malformed = "{\"Mixins\": [}";
        Files.writeString(config, malformed, StandardCharsets.UTF_8);
        Class<?> pluginType = Class.forName(PLUGIN);
        Object plugin = pluginType.getConstructor().newInstance();
        Throwable failure = null;
        try {
            pluginType.getMethod("onLoad", String.class).invoke(plugin, MIXIN_PACKAGE);
        } catch (InvocationTargetException wrapped) {
            failure = wrapped.getCause();
        }
        require(failure instanceof Error && failure.getClass().getSimpleName().equals("MixinInitialisationError"),
                "malformed config must stop startup instead of being swallowed by Mixin");
        require(failure.getMessage().contains(CONFIG), "malformed config error must identify the config path");
        require(failure.getMessage().toLowerCase().contains("malformed"), "malformed config error must explain the failure");
        require(Files.readString(config).equals(malformed), "malformed config must not be replaced");
        require(!(boolean) Class.forName(OPTIONS).getMethod("hasMixinSelection").invoke(null), "malformed config must publish no selection");
    }

    private static void verifyMissingConfig(Path config) throws Exception {
        Class<?> pluginType = Class.forName(PLUGIN);
        Object plugin = pluginType.getConstructor().newInstance();
        pluginType.getMethod("onLoad", String.class).invoke(plugin, MIXIN_PACKAGE);
        require(Files.isRegularFile(config), "missing config must be written");
        require(!Files.exists(config.resolveSibling("config.json")), "early bootstrap must not create native runtime config");
    }

    private static void verifyMapping() throws Exception {
        var field = Class.forName(PLUGIN).getDeclaredField("ALL_MIXINS");
        field.setAccessible(true);
        Set<?> mapped = (Set<?>) field.get(null);
        try (var input = PolicyMixinConfigRegression.class.getResourceAsStream("/refixes.mixins.json")) {
            require(input != null, "mixin manifest must be on the regression classpath");
            var array = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("mixins");
            Set<String> listed = new HashSet<>();
            array.forEach(value -> require(listed.add(value.getAsString()), "duplicate manifest mixin " + value));
            require(mapped.equals(listed), "every listed mixin must have exactly one policy mapping: mapped=" + mapped + " listed=" + listed);
        }
        String patches = Files.readString(Path.of(System.getProperty("refixes.docs"), "patches.md"));
        var switches = Class.forName(PLUGIN).getDeclaredField("SWITCH_PATHS");
        switches.setAccessible(true);
        for (Object path : (Set<?>) switches.get(null)) {
            String name = path.toString().substring(path.toString().indexOf('.', "Mixins.".length()) + 1);
            require(patches.contains("`" + name + "`"), "docs/patches.md must describe the switch " + path);
        }
    }

    private static void verifyExplicitSelection(Path config) throws Exception {
        Files.writeString(config, """
                {"Mixins": {
                  "Correctness": {"WorldTransfer": false, "MountPlugin": false, "TrackedPlacementOnAddRemove": false},
                  "Compatibility": {"ServerAuthManager": false},
                  "Optimizations": {"CodecMetadata": false},
                  "Tuning": {"PathfindingLimits": true},
                  "Features": {"SharedInstances": true},
                  "Recovery": {"ShutdownSaveTimeout": true},
                  "Experimental": {"PathfindingBudget": true}
                }}
                """);
        String runtimeConfig = """
                {"Runtime": {"Recovery": {
                  "UnknownBlockCleaner": {"Enabled": true, "ScanFluids": true},
                  "Watchdog": {"AutoRestartWorlds": true}
                }}}
                """;
        Files.writeString(config.resolveSibling("config.json"), runtimeConfig);
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        type.getMethod("onLoad", String.class).invoke(plugin, MIXIN_PACKAGE);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (String mixin : Set.of("MixinBodyMotionFindBase", "MixinWorldPathfindingBudget", "MixinAStarLimits",
                "MixinStoreShutdown", "MixinWorldShutdown", "MixinInstancesPlugin",
                "MixinFluidSection", "MixinTickingThreadAccess")) {
            require(applies(plugin, apply, mixin), "explicit toggle must select " + mixin);
        }
        for (String mixin : Set.of("MixinWorldTransfer", "MixinCodecMetadata", "MixinBlockComponentSectionAccess",
                "MixinServerAuthManager", "MixinOptions", "MixinMountPlugin", "MixinTrackedPlacementOnAddRemove")) {
            require(!applies(plugin, apply, mixin), "independent disabled toggle must leave out " + mixin);
        }
        var saved = JsonParser.parseString(Files.readString(config)).getAsJsonObject().getAsJsonObject("Mixins");
        require(Files.readString(config.resolveSibling("config.json")).equals(runtimeConfig), "early bootstrap must not rewrite native runtime config");
        require(saved.getAsJsonObject("Experimental").get("PathfindingBudget").getAsBoolean(), "pathfinding budget intent must survive serialization");
        require(!saved.getAsJsonObject("Correctness").get("MountPlugin").getAsBoolean(), "explicit false intent must survive serialization");
    }

    private static void verifyMisplacedRuntime(Path config) throws Exception {
        Files.writeString(config, """
                {"Mixins":{"Correctness":{"WorldOwnership":false}},
                 "Runtime":{"Recovery":{
                  "UnknownBlockCleaner":{"Enabled":true,"ScanContainers":true,"ScanFluids":true},
                  "Watchdog":{"AutoRestartWorlds":true}
                }}}
                """);
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        type.getMethod("onLoad", String.class).invoke(plugin, MIXIN_PACKAGE);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (String mixin : Set.of("MixinBlockComponentSectionAccess", "MixinFluidSection", "MixinTickingThreadAccess")) {
            require(!applies(plugin, apply, mixin), "misplaced runtime key must not activate " + mixin);
        }
    }

    private static void verifyReload(Path config) throws Exception {
        Class<?> type = Class.forName(PLUGIN);
        Class<?> options = Class.forName(OPTIONS);
        Object plugin = type.getConstructor().newInstance();
        Method load = type.getMethod("onLoad", String.class);
        Method lookup = options.getMethod("isMixinEnabled", String.class);
        try (URLClassLoader isolated = new URLClassLoader(new URL[] {classLocation(options)}, ClassLoader.getPlatformClassLoader())) {
            Method isolatedLookup = isolated.loadClass(OPTIONS).getMethod("isMixinEnabled", String.class);
            load.invoke(plugin, MIXIN_PACKAGE);
            require(enabled(lookup, "MixinMountPlugin") && enabled(isolatedLookup, "MixinMountPlugin"), "bootstrap selection must reach both classloaders");
            Files.writeString(config, "{\"Mixins\":{\"Correctness\":{\"MountPlugin\":false}}}");
            load.invoke(plugin, MIXIN_PACKAGE);
            require(!enabled(lookup, "MixinMountPlugin") && !enabled(isolatedLookup, "MixinMountPlugin"), "reloaded selection must invalidate both classloader caches");
            Files.writeString(config, "{");
            require(loadFailure(type, plugin) instanceof Error, "invalid reload must stop startup");
            require(!enabled(lookup, "MixinCodecMetadata") && !enabled(isolatedLookup, "MixinCodecMetadata"), "failed reload must clear selected mixins");
            require(!(boolean) options.getMethod("hasMixinSelection").invoke(null), "failed reload must clear the published selection");
        }
    }

    private static void verifyDependency(Path config, String original, String feature, String dependency, String mixin)
            throws Exception {
        Files.writeString(config, original);
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> require(loadFailure(type, plugin) == null, "a switch without its dependency must not stop startup"));
        require(log.contains("[Refixes] " + feature + " is off on this start because it needs " + dependency + ", which is off."),
                "the switch that is turned off and the switch it needs must be named:\n" + log);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        require(!applies(plugin, apply, mixin), "a switch without its dependency must not apply " + mixin);
        require(applies(plugin, apply, "MixinMountPlugin"), "the other switches must still apply");
        JsonElement saved = JsonParser.parseString(Files.readString(config));
        for (String part : feature.split("\\.")) saved = saved.getAsJsonObject().get(part);
        require(saved.getAsBoolean(), "the file must keep the owner's value of " + feature);
    }

    private static final String ROUND_TRIP = "{\"Mixins\":{"
            + "\"Backports\":{\"ShutdownRemoval\":false},"
            + "\"Correctness\":{\"PortalDeviceWorldClose\":false,\"RocksDbColumnRemoval\":false,\"WorldRetirement\":true},"
            + "\"Optimizations\":{\"RocksDbCompression\":true,\"SkipEmptyLightSections\":true}}}";

    private static void verifyRoundTrip(Path config, boolean first) throws Exception {
        if (first) Files.writeString(config, ROUND_TRIP);
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> require(loadFailure(type, plugin) == null, "round trip start must not fail"));
        require(!log.contains("[Refixes] Refixes.json: "),
                "a start must not remove a switch of the other Hytale line:\n" + log);
        JsonObject mixins = JsonParser.parseString(Files.readString(config)).getAsJsonObject().getAsJsonObject("Mixins");
        require(!mixins.getAsJsonObject("Backports").get("ShutdownRemoval").getAsBoolean(), "ShutdownRemoval=false lost: " + mixins);
        require(!mixins.getAsJsonObject("Correctness").get("PortalDeviceWorldClose").getAsBoolean(), "PortalDeviceWorldClose=false lost: " + mixins);
        require(!mixins.getAsJsonObject("Correctness").get("RocksDbColumnRemoval").getAsBoolean(), "RocksDbColumnRemoval=false lost: " + mixins);
        require(mixins.getAsJsonObject("Correctness").get("WorldRetirement").getAsBoolean(), "WorldRetirement=true lost: " + mixins);
        require(mixins.getAsJsonObject("Optimizations").get("RocksDbCompression").getAsBoolean(), "RocksDbCompression=true lost: " + mixins);
        require(mixins.getAsJsonObject("Optimizations").get("SkipEmptyLightSections").getAsBoolean(), "SkipEmptyLightSections=true lost: " + mixins);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (String mixin : Set.of("MixinShutdownWorldRemoval", "MixinPortalDeviceWorldClose", "MixinRocksDbColumnRemoval"))
            require(!applies(plugin, apply, mixin), "a switch turned off before the other Hytale line ran must stay off: " + mixin);
        require(applies(plugin, apply, "MixinRocksDbCompression"), "RocksDbCompression=true must still apply");
    }

    private static void verifyRoundTripOther(Path config) throws Exception {
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> require(loadFailure(type, plugin) == null, "round trip start must not fail"));
        require(!log.contains("[Refixes] Refixes.json: "), "Hytale 0.8 must keep the 0.7 switches:\n" + log);
        JsonObject mixins = JsonParser.parseString(Files.readString(config)).getAsJsonObject().getAsJsonObject("Mixins");
        require(!mixins.getAsJsonObject("Backports").get("ShutdownRemoval").getAsBoolean(), "ShutdownRemoval=false lost on 0.8: " + mixins);
        require(mixins.getAsJsonObject("Optimizations").get("SkipEmptyLightSections").getAsBoolean(), "SkipEmptyLightSections=true lost on 0.8: " + mixins);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        require(applies(plugin, apply, "MixinFloodLightCalculation"), "SkipEmptyLightSections=true must apply on 0.8");
        require(!applies(plugin, apply, "MixinShutdownWorldRemoval"), "a 0.7 switch must not apply on 0.8");
    }

    private static void verifyTransferDependency(Path config) throws Exception {
        String original = "{\"Mixins\":{\"Correctness\":{\"StorageOrdering\":false}}}";
        if (!hytale08()) {
            verifyDependency(config, original, "Mixins.Correctness.WorldTransfer", "Mixins.Correctness.StorageOrdering", "MixinWorldTransfer");
            return;
        }
        Files.writeString(config, original);
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        require(loadFailure(type, plugin) == null, "StorageOrdering=false must not stop startup on Hytale 0.8");
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        require(applies(plugin, apply, "MixinWorldTransfer"), "WorldTransfer must apply without StorageOrdering on Hytale 0.8");
    }

    private static void verifyFailedWrite(Path config) throws Exception {
        Files.writeString(config, "{\"Mixins\":{\"Correctness\":{\"MountPlugin\":false}}}");
        Path parent = config.getParent();
        require(parent.toFile().setWritable(false), "could not make the config directory read-only");
        try {
            Class<?> type = Class.forName(PLUGIN);
            Object plugin = type.getConstructor().newInstance();
            require(loadFailure(type, plugin) == null, "failed config write must not stop startup");
            Method lookup = Class.forName(OPTIONS).getMethod("isMixinEnabled", String.class);
            require(enabled(lookup, "MixinWorldTransfer"),
                    "failed write must still publish the selection");
            require(!enabled(lookup, "MixinMountPlugin"), "failed write must keep the configured selection");
        } finally {
            parent.toFile().setWritable(true);
        }
    }

    private static void verifyAllDisabled(Path config) throws Exception {
        Class<?> type = Class.forName(PLUGIN);
        Class<?> options = Class.forName(OPTIONS);
        Object plugin = type.getConstructor().newInstance();
        Method load = type.getMethod("onLoad", String.class);
        load.invoke(plugin, MIXIN_PACKAGE);
        JsonObject root = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        disableAll(root.getAsJsonObject("Mixins"));
        Files.writeString(config, root.toString());
        load.invoke(plugin, MIXIN_PACKAGE);
        require((boolean) options.getMethod("hasMixinSelection").invoke(null), "explicit all-disabled selection must still be published");
        var field = type.getDeclaredField("ALL_MIXINS");
        field.setAccessible(true);
        Method lookup = options.getMethod("isMixinEnabled", String.class);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (Object mixin : (Set<?>) field.get(null)) {
            require(!enabled(lookup, (String) mixin) && !applies(plugin, apply, (String) mixin), "all-disabled selection must leave out " + mixin);
        }
    }

    private static void verifyVersionAccepted(Path config) throws Exception {
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> require(loadFailure(type, plugin) == null, "supported version must not stop startup"));
        require(!log.contains("Early mixins disabled") && !log.contains("Version gate overridden"),
                "supported version must pass the gate without rejection or override:\n" + log);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        require(applies(plugin, apply, "MixinWorldTransfer"), "supported version must apply the configured selection");
        require(Files.exists(config), "supported version must write the config");
    }

    private static void verifyVersionRejected(Path config, String original, String reason) throws Exception {
        String version = serverVersion();
        Files.writeString(config, original);
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> require(loadFailure(type, plugin) == null, "unsupported version must not stop startup"));
        require(log.contains(reason), "rejection must give its reason \"" + reason + "\":\n" + log);
        require(log.contains("Early mixins disabled for Hytale " + version), "rejection must name the server version:\n" + log);
        require(log.contains("Refixes supports Hytale 0.7.7 and 0.8.0-pre.6."), "rejection must name the supported versions:\n" + log);
        require((boolean) Class.forName(OPTIONS).getMethod("hasMixinSelection").invoke(null), "rejection must publish an empty selection");
        Method lookup = Class.forName(OPTIONS).getMethod("isMixinEnabled", String.class);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (String mixin : allMixins()) {
            require(!enabled(lookup, mixin) && !applies(plugin, apply, mixin), "unsupported version must leave out " + mixin);
        }
        require(Files.readString(config).equals(original), "unsupported version must not write the config");
    }

    private static void verifyVersionUnchangedUnavailable(Path config, String otherServerVersions) throws Exception {
        if (otherServerVersions != null)
            Files.writeString(config, "{\"OtherServerVersions\":\"" + otherServerVersions + "\"}");
        String version = serverVersion();
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> require(loadFailure(type, plugin) == null, "a failed comparison must not stop startup"));
        require(log.contains("Could not compare the Hytale " + version + " code"), "failed comparison must be reported:\n" + log);
        require(log.contains("\tat "), "failed comparison must print its stack trace:\n" + log);
        require(log.contains("Early mixins disabled for Hytale " + version), "failed comparison must disable early mixins:\n" + log);
        Method lookup = Class.forName(OPTIONS).getMethod("isMixinEnabled", String.class);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (String mixin : allMixins()) {
            require(!enabled(lookup, mixin) && !applies(plugin, apply, mixin), "failed comparison must leave out " + mixin);
        }
        if (otherServerVersions == null) require(!Files.exists(config), "failed comparison must not write the config");
    }

    private static void verifyVersionOverride(Path config, String original, String logged) throws Exception {
        String version = serverVersion();
        Files.writeString(config, original);
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> require(loadFailure(type, plugin) == null, "override must not stop startup"));
        require(log.contains("Version gate overridden for Hytale " + version), "override must be logged:\n" + log);
        require(log.contains(logged), "override start must log " + logged + ":\n" + log);
        Method lookup = Class.forName(OPTIONS).getMethod("isMixinEnabled", String.class);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (String mixin : Set.of("MixinStoreShutdown", "MixinWorldShutdown")) {
            require(enabled(lookup, mixin) && applies(plugin, apply, mixin), "override must apply configured " + mixin);
        }
        JsonObject saved = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        require(saved.get("OtherServerVersions").getAsString().equals("AllPatches"),
                "override value must survive serialization: " + saved);
        require(saved.getAsJsonObject("Mixins").getAsJsonObject("Recovery").get("ShutdownSaveTimeout").getAsBoolean(),
                "toggle intent must survive serialization");
    }

    private static void verifyVersionOverrideDependency(Path config) throws Exception {
        serverVersion();
        String original = "{\"OtherServerVersions\":\"AllPatches\",\"Mixins\":{\"Correctness\":{\"ParallelTaskLifecycle\":false},"
                + "\"Experimental\":{\"Parallel\":{\"CollectVisible\":true}}}}";
        verifyDependency(config, original, "Mixins.Experimental.Parallel.CollectVisible", "Mixins.Correctness.ParallelTaskLifecycle", "MixinCollectVisibleParallel");
    }

    private static boolean hytale08() throws Exception {
        URL main = PolicyMixinConfigRegression.class.getResource("/com/hypixel/hytale/Main.class");
        require(main != null && main.openConnection() instanceof java.net.JarURLConnection, "Hytale server JAR missing");
        String version = ((java.net.JarURLConnection) main.openConnection()).getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
        return version != null && version.startsWith("0.8.");
    }

    private static String serverVersion() {
        String version = System.getProperty(SERVER_VERSION);
        require(version != null, "server version stub missing");
        return version;
    }

    private interface Action {
        void run() throws Exception;
    }

    private static String captured(Action action) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int value) {
                buffer.write(value);
                original.write(value);
            }
        }, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static Set<String> allMixins() throws Exception {
        var field = Class.forName(PLUGIN).getDeclaredField("ALL_MIXINS");
        field.setAccessible(true);
        Set<String> mixins = new HashSet<>();
        for (Object mixin : (Set<?>) field.get(null)) mixins.add((String) mixin);
        return mixins;
    }

    private static void disableAll(JsonObject object) {
        for (var entry : object.entrySet()) {
            if (entry.getValue().isJsonObject()) disableAll(entry.getValue().getAsJsonObject());
            else object.addProperty(entry.getKey(), false);
        }
    }

    private static Throwable loadFailure(Class<?> type, Object plugin) throws Exception {
        try {
            type.getMethod("onLoad", String.class).invoke(plugin, MIXIN_PACKAGE);
            return null;
        } catch (InvocationTargetException wrapped) {
            return wrapped.getCause();
        }
    }

    private static URL classLocation(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }

    private static boolean enabled(Method lookup, String simpleName) throws Exception {
        return (boolean) lookup.invoke(null, simpleName);
    }

    private static boolean applies(Object plugin, Method shouldApply, String simpleName) throws Exception {
        return (boolean) shouldApply.invoke(plugin, "test.Target", MIXIN_PACKAGE + "." + simpleName);
    }
}
