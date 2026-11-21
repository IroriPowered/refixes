package local.refixes.policy;

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
        runInWorkingDirectory("misplaced-runtime");
        runInWorkingDirectory("reload");
        runInWorkingDirectory("failed-write");
        runInWorkingDirectory("all-disabled");
        runInWorkingDirectory("version-accepted", "0.8.0-pre.6");
        runInWorkingDirectory("version-accepted", "0.8.0-pre.6.1");
        runInWorkingDirectory("version-older", "0.8.0-pre.5");
        runInWorkingDirectory("version-older", "0.8.0-pre.5.2");
        runInWorkingDirectory("version-older", "0.8.0-pre.1");
        runInWorkingDirectory("version-older", "0.7.7");
        runInWorkingDirectory("version-older-allowed-unchanged", "0.8.0-pre.5");
        runInWorkingDirectory("version-older-legacy-false", "0.8.0-pre.5");
        runInWorkingDirectory("version-newer-minor", "0.9.0");
        runInWorkingDirectory("version-newer-minor", "0.9.0-pre.1");
        runInWorkingDirectory("version-unreadable", "0.8");
        runInWorkingDirectory("version-unreadable", "0.8.0-beta.1");
        runInWorkingDirectory("version-never", "0.8.0-pre.7");
        runInWorkingDirectory("version-never-migrated", "0.8.0");
        runInWorkingDirectory("version-never-lowercase", "0.8.0-pre.7");
        runInWorkingDirectory("version-invalid-logged", "0.9.0");
        runInWorkingDirectory("version-false-logged", "0.8.1");
        runInWorkingDirectory("version-string-logged", "0.8.0-pre.7");
        runInWorkingDirectory("version-override", "0.9.0");
        runInWorkingDirectory("version-override-new-key", "0.8.0-pre.5");
        runInWorkingDirectory("version-override-both-keys", "0.9.0");
        runInWorkingDirectory("version-override-dependency", "0.8.0-pre.5");
        runInWorkingDirectory("version-unchanged-unavailable", "0.8.0-pre.7");
        runInWorkingDirectory("version-unchanged-unavailable", "0.8.0");
        runInWorkingDirectory("version-unchanged-unavailable-all", "0.9.0");
        runInWorkingDirectory("other-versions-migrated", "0.8.0-pre.6");
        System.out.println("PASS early mixin policy configuration regressions");
    }

    private static void runInWorkingDirectory(String scenario) throws Exception {
        runInWorkingDirectory(scenario, null);
    }

    private static void runInWorkingDirectory(String scenario, String serverVersion) throws Exception {
        Path workingDirectory = Files.createTempDirectory("refixes-policy-");
        String classPath = absoluteClassPath();
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString()));
        if (serverVersion != null) {
            classPath = serverStub(workingDirectory, serverVersion) + System.getProperty("path.separator") + classPath;
            command.add("-D" + SERVER_VERSION + "=" + serverVersion);
        }
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
            check(main != null, "Hytale server must be on the regression classpath");
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
                    "Mixins.Correctness.ParallelTaskLifecycle");
            case "storage-dependency" -> verifyDependency(config,
                    "{\"Mixins\":{\"Features\":{\"SharedInstances\":true,\"SharedInstanceStorage\":false}}}",
                    "Mixins.Features.SharedInstanceStorage");
            case "misplaced-runtime" -> verifyMisplacedRuntime(config);
            case "reload" -> verifyReload(config);
            case "failed-write" -> verifyFailedWrite(config);
            case "all-disabled" -> verifyAllDisabled(config);
            case "version-accepted" -> verifyVersionAccepted(config);
            case "version-older" -> verifyVersionRejected(config, "{}",
                    "is older than 0.8.0-pre.6, the version this Refixes build was made for. Early patches apply on"
                            + " older versions only when OtherServerVersions is AllPatches.");
            case "version-older-allowed-unchanged" -> verifyVersionRejected(config,
                    "{\"OtherServerVersions\":\"AllNewerVersions\"}",
                    "Early patches apply on older versions only when OtherServerVersions is AllPatches.");
            case "version-older-legacy-false" -> verifyVersionRejected(config,
                    "{\"AllowUnsupportedServerVersion\":false}",
                    "Refixes.json: Removed AllowUnsupportedServerVersion=false (old default)");
            case "version-newer-minor" -> verifyVersionRejected(config, "{}",
                    "On versions after Hytale 0.8 early patches apply only when OtherServerVersions is"
                            + " AllNewerVersions or AllPatches.");
            case "version-unreadable" -> verifyVersionRejected(config, "{}",
                    "Refixes cannot compare the Hytale version " + serverVersion() + " with 0.8.0-pre.6");
            case "version-never" -> verifyVersionRejected(config,
                    "{\"OtherServerVersions\":\"Never\"}",
                    "OtherServerVersions is Never.");
            case "version-never-migrated" -> verifyVersionRejected(config,
                    "{\"ApplyUnchangedPatchesOnUnsupportedServerVersion\":false}",
                    "OtherServerVersions is Never.");
            case "version-never-lowercase" -> verifyVersionRejected(config,
                    "{\"OtherServerVersions\":\"never\"}",
                    "OtherServerVersions is Never.");
            case "version-invalid-logged" -> verifyVersionRejected(config,
                    "{\"OtherServerVersions\":\"AllNewerVersion\"}",
                    "Refixes.json: Replaced invalid value OtherServerVersions=\"AllNewerVersion\" with the default");
            case "version-false-logged" -> verifyVersionRejected(config,
                    "{\"ApplyUnchangedPatchesOnUnsupportedServerVersion\":false}",
                    "Refixes.json: Migrated ApplyUnchangedPatchesOnUnsupportedServerVersion=false to"
                            + " OtherServerVersions=Never");
            case "version-string-logged" -> verifyVersionRejected(config,
                    "{\"ApplyUnchangedPatchesOnUnsupportedServerVersion\":\"never\"}",
                    "Refixes.json: Migrated ApplyUnchangedPatchesOnUnsupportedServerVersion=\"never\" to"
                            + " OtherServerVersions=Never");
            case "other-versions-migrated" -> verifyOtherServerVersionsMigration(config);
            case "version-override" -> verifyVersionOverride(config,
                    "{\"AllowUnsupportedServerVersion\":true,\"Mixins\":{\"Recovery\":{\"ShutdownSaveTimeout\":true}}}",
                    "Migrated AllowUnsupportedServerVersion=true to OtherServerVersions=AllPatches");
            case "version-override-new-key" -> verifyVersionOverride(config,
                    "{\"OtherServerVersions\":\"allpatches\",\"Mixins\":{\"Recovery\":{\"ShutdownSaveTimeout\":true}}}",
                    "Version gate overridden for Hytale 0.8.0-pre.5 by OtherServerVersions=AllPatches");
            case "version-override-both-keys" -> verifyVersionOverride(config,
                    "{\"AllowUnsupportedServerVersion\":true,\"ApplyUnchangedPatchesOnUnsupportedServerVersion\":"
                            + "\"Never\",\"Mixins\":{\"Recovery\":{\"ShutdownSaveTimeout\":true}}}",
                    "Removed ApplyUnchangedPatchesOnUnsupportedServerVersion=\"Never\", AllowUnsupportedServerVersion"
                            + "=true takes precedence");
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
                    "Optimizations": {"CollectVisible": false, "AStarBase": true},
                    "Crashfixes": {"BlockSectionSafety": true, "EntityChunkLoadingSystem": true},
                    "Helpers": {"World": false},
                    "Experimental": {"ShutdownSaveTimeout": true, "SkipEmptyLightSections": false, "ConnectionBackpressure": true, "Parallel": {"CollectVisible": true, "AllSystems": true, "Steering": true}}
                  },
                  "Early": {"PathfindingMaxPathLength": 321},
                  "Runtime": {"Recovery": {"UnknownBlockCleaner": {"Enabled": true, "ScanContainers": true, "ScanFluids": false}}},
                  "Listeners": {"UnknownBlockCleaner": false, "UnknownBlockCleanerScanContainers": true},
                  "HypixelServices": {"LiveConfig": true, "Preserved": {"Enabled": true}},
                  "Other": {"Keep": "unchanged"}
                }
                """;
        Files.writeString(config, original, StandardCharsets.UTF_8);
        Files.writeString(config.resolveSibling("config.json"), """
                {"Runtime":{"Recovery":{"UnknownBlockCleaner":{"Enabled":true,"ScanContainers":true,"ScanFluids":false}}}}
                """);

        Class<?> options = Class.forName(OPTIONS);
        Method isMixinEnabled = options.getMethod("isMixinEnabled", String.class);
        check(!enabled(isMixinEnabled, "MixinMountPlugin"), "mixin lookup must be false before bootstrap");
        Method hasSelection = options.getMethod("hasMixinSelection");
        check(!(boolean) hasSelection.invoke(null), "selection must be absent before bootstrap");

        try (URLClassLoader isolated = new URLClassLoader(
                new URL[] {classLocation(options)}, ClassLoader.getPlatformClassLoader())) {
            Class<?> isolatedOptions = isolated.loadClass(OPTIONS);
            Method isolatedLookup = isolatedOptions.getMethod("isMixinEnabled", String.class);
            check(!enabled(isolatedLookup, "MixinMountPlugin"), "isolated mixin lookup must be false before bootstrap");

            Class<?> pluginType = Class.forName(PLUGIN);
            Object plugin = pluginType.getConstructor().newInstance();
            Method preApply = pluginType.getMethod("shouldApplyMixin", String.class, String.class);
            check(!applies(plugin, preApply, "MixinMountPlugin"), "uninitialized plugin must select no mixins");
            pluginType.getMethod("onLoad", String.class).invoke(plugin, MIXIN_PACKAGE);
            Method shouldApply = pluginType.getMethod("shouldApplyMixin", String.class, String.class);

            check(applies(plugin, shouldApply, "MixinStoreShutdown"), "explicit legacy shutdown timeout toggle must migrate");
            check(applies(plugin, shouldApply, "MixinWorldShutdown"), "migrated shutdown timeout toggle must select both mixins");
            check(applies(plugin, shouldApply, "MixinLiveConfigModule"), "explicit legacy live config switch must migrate");
            check(applies(plugin, shouldApply, "MixinCollectVisibleParallel"), "current parallel CollectVisible setting must enable its feature");
            check(applies(plugin, shouldApply, "MixinBlockComponentSectionAccess"), "current container cleaner dependency must select its accessor");
            check(!applies(plugin, shouldApply, "MixinFluidSection"), "disabled fluid cleaning must not select its accessor");
            check(applies(plugin, shouldApply, "MixinChunkGridAccess"), "shared storage access must be independent of instance creation");
            check(enabled(isMixinEnabled, "MixinMountPlugin"), "bootstrap must publish selected mixins before lazy targets load");
            check((boolean) hasSelection.invoke(null), "successful bootstrap must publish a selection");
            check(!enabled(isMixinEnabled, null), "null mixin lookup must be false");
            check(!enabled(isMixinEnabled, "MixinNotConfigured"), "unknown mixin lookup must be false");
            check(!applies(plugin, shouldApply, "MixinNotConfigured"), "unknown mixins must never apply");
            check(enabled(isMixinEnabled, "MixinBlockComponentSectionAccess"), "bootstrap must publish dependency mixins");
            check(!enabled(isMixinEnabled, "MixinCollectVisible"), "disabled mixins must not be published as active");
            check(enabled(isolatedLookup, "MixinMountPlugin"), "selected mixins must cross the early/main classloader boundary");
            check(!enabled(isolatedLookup, "MixinCollectVisible"), "isolated lookup must preserve disabled mixin state");

            JsonObject root = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
            JsonObject mixins = root.getAsJsonObject("Mixins");
            check(mixins.getAsJsonObject("Experimental").getAsJsonObject("Parallel").get("CollectVisible").getAsBoolean(), "parallel key missing");
            check(mixins.getAsJsonObject("Tuning").get("PathfindingLimits").getAsJsonPrimitive().isBoolean(), "pathfinding limit key missing");
            check(!mixins.getAsJsonObject("Optimizations").has("AStarBase"), "retired AStar toggle must not be serialized");
            check(!mixins.getAsJsonObject("Experimental").has("SkipEmptyLightSections"), "retired light toggle must not be serialized");
            check(!applies(plugin, shouldApply, "MixinFloodLightCalculation"), "legacy light toggle at its old default must not enable the optimization");
            check(!mixins.getAsJsonObject("Recovery").has("InvalidSavedHolders"), "legacy saved-holder toggle must not be serialized");
            check(!mixins.getAsJsonObject("Experimental").has("ConnectionBackpressure"), "retired backpressure toggle must not be serialized");
            for (String key : Set.of("AllSystems", "Steering"))
                check(!mixins.getAsJsonObject("Experimental").getAsJsonObject("Parallel").has(key), "retired parallel toggle must not be serialized: " + key);
            check(!mixins.has("Crashfixes") && !mixins.has("Helpers"), "retired categories must be removed");
            check(!mixins.getAsJsonObject("Experimental").getAsJsonObject("Parallel").has("RelaxStoreAsserts"), "derived store-context toggle must not be serialized");
            check(!mixins.getAsJsonObject("Experimental").getAsJsonObject("Parallel").has("FluidReplicateChanges"), "derived fluid replication toggle must not be serialized");
            check(!mixins.getAsJsonObject("Experimental").getAsJsonObject("Parallel").has("ChunkReplicateChanges"), "derived chunk replication toggle must not be serialized");
            check(root.getAsJsonObject("Early").get("PathfindingMaxPathLength").getAsInt() == 321, "Early root section was not preserved");
            check(root.getAsJsonObject("Runtime").getAsJsonObject("Recovery").getAsJsonObject("UnknownBlockCleaner").get("ScanContainers").getAsBoolean(), "Runtime root section was not preserved");
            check(!root.getAsJsonObject("Listeners").get("UnknownBlockCleaner").getAsBoolean(), "Listeners legacy root section was not preserved");
            check(root.getAsJsonObject("HypixelServices").getAsJsonObject("Preserved").get("Enabled").getAsBoolean(), "HypixelServices root section was not preserved");
            check(!root.getAsJsonObject("HypixelServices").has("LiveConfig"), "migrated live config key must be removed");
            check(root.getAsJsonObject("Other").get("Keep").getAsString().equals("unchanged"), "unrelated root section was not preserved");
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
        check(failure instanceof Error && failure.getClass().getSimpleName().equals("MixinInitialisationError"),
                "malformed config must stop startup instead of being swallowed by Mixin");
        check(failure.getMessage().contains(CONFIG), "malformed config error must identify the config path");
        check(failure.getMessage().toLowerCase().contains("malformed"), "malformed config error must explain the failure");
        check(Files.readString(config).equals(malformed), "malformed config must not be replaced");
        check(!(boolean) Class.forName(OPTIONS).getMethod("hasMixinSelection").invoke(null), "malformed config must publish no selection");
    }

    private static void verifyMissingConfig(Path config) throws Exception {
        Class<?> pluginType = Class.forName(PLUGIN);
        Object plugin = pluginType.getConstructor().newInstance();
        pluginType.getMethod("onLoad", String.class).invoke(plugin, MIXIN_PACKAGE);
        check(Files.isRegularFile(config), "missing config must be written");
        check(!Files.exists(config.resolveSibling("config.json")), "early bootstrap must not create native runtime config");
    }

    private static void verifyMapping() throws Exception {
        var field = Class.forName(PLUGIN).getDeclaredField("ALL_MIXINS");
        field.setAccessible(true);
        Set<?> mapped = (Set<?>) field.get(null);
        try (var input = PolicyMixinConfigRegression.class.getResourceAsStream("/refixes.mixins.json")) {
            check(input != null, "mixin manifest must be on the regression classpath");
            var array = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("mixins");
            Set<String> listed = new HashSet<>();
            array.forEach(value -> check(listed.add(value.getAsString()), "duplicate manifest mixin " + value));
            check(mapped.equals(listed), "every listed mixin must have exactly one policy mapping: mapped=" + mapped + " listed=" + listed);
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
                "MixinStoreShutdown", "MixinWorldShutdown", "MixinInstancesPlugin", "MixinFluidSection", "MixinTickingThreadAccess")) {
            check(applies(plugin, apply, mixin), "explicit toggle must select " + mixin);
        }
        for (String mixin : Set.of("MixinWorldTransfer", "MixinCodecMetadata", "MixinBlockComponentSectionAccess",
                "MixinServerAuthManager", "MixinOptions", "MixinMountPlugin", "MixinTrackedPlacementOnAddRemove")) {
            check(!applies(plugin, apply, mixin), "independent disabled toggle must leave out " + mixin);
        }
        var saved = JsonParser.parseString(Files.readString(config)).getAsJsonObject().getAsJsonObject("Mixins");
        check(Files.readString(config.resolveSibling("config.json")).equals(runtimeConfig), "early bootstrap must not rewrite native runtime config");
        check(saved.getAsJsonObject("Experimental").get("PathfindingBudget").getAsBoolean(), "pathfinding budget intent must survive serialization");
        check(!saved.getAsJsonObject("Correctness").get("MountPlugin").getAsBoolean(), "explicit false intent must survive serialization");
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
            check(!applies(plugin, apply, mixin), "misplaced runtime key must not activate " + mixin);
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
            check(enabled(lookup, "MixinMountPlugin") && enabled(isolatedLookup, "MixinMountPlugin"), "bootstrap selection must reach both classloaders");
            Files.writeString(config, "{\"Mixins\":{\"Correctness\":{\"MountPlugin\":false}}}");
            load.invoke(plugin, MIXIN_PACKAGE);
            check(!enabled(lookup, "MixinMountPlugin") && !enabled(isolatedLookup, "MixinMountPlugin"), "reloaded selection must invalidate both classloader caches");
            Files.writeString(config, "{");
            check(loadFailure(type, plugin) instanceof Error, "invalid reload must stop startup");
            check(!enabled(lookup, "MixinCodecMetadata") && !enabled(isolatedLookup, "MixinCodecMetadata"), "failed reload must clear selected mixins");
            check(!(boolean) options.getMethod("hasMixinSelection").invoke(null), "failed reload must clear the published selection");
        }
    }

    private static void verifyDependency(Path config, String original, String dependency) throws Exception {
        Files.writeString(config, original);
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        Throwable failure = loadFailure(type, plugin);
        check(failure instanceof Error, "missing dependency must stop startup");
        check(failure.getMessage().contains(dependency), "missing dependency error must name " + dependency);
        check(Files.readString(config).equals(original), "invalid dependency config must preserve user intent");
        check(!applies(plugin, type.getMethod("shouldApplyMixin", String.class, String.class), "MixinMountPlugin"), "failed bootstrap must select no mixins");
        check(!enabled(Class.forName(OPTIONS).getMethod("isMixinEnabled", String.class), "MixinMountPlugin"), "failed bootstrap must publish no mixins");
        check(!(boolean) Class.forName(OPTIONS).getMethod("hasMixinSelection").invoke(null), "failed bootstrap must publish no selection");
    }

    private static void verifyFailedWrite(Path config) throws Exception {
        Files.writeString(config, "{\"Mixins\":{\"Correctness\":{\"MountPlugin\":false}}}");
        Path parent = config.getParent();
        check(parent.toFile().setWritable(false), "could not make the config directory read-only");
        try {
            Class<?> type = Class.forName(PLUGIN);
            Object plugin = type.getConstructor().newInstance();
            check(loadFailure(type, plugin) == null, "failed config write must not stop startup");
            Method lookup = Class.forName(OPTIONS).getMethod("isMixinEnabled", String.class);
            check(enabled(lookup, "MixinWorldTransfer"), "failed write must still publish the selection");
            check(!enabled(lookup, "MixinMountPlugin"), "failed write must keep the configured selection");
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
        check((boolean) options.getMethod("hasMixinSelection").invoke(null), "explicit all-disabled selection must still be published");
        var field = type.getDeclaredField("ALL_MIXINS");
        field.setAccessible(true);
        Method lookup = options.getMethod("isMixinEnabled", String.class);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (Object mixin : (Set<?>) field.get(null)) {
            check(!enabled(lookup, (String) mixin) && !applies(plugin, apply, (String) mixin), "all-disabled selection must leave out " + mixin);
        }
    }

    private static void verifyVersionAccepted(Path config) throws Exception {
        serverVersion();
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> check(loadFailure(type, plugin) == null, "supported version must not stop startup"));
        check(!log.contains("Early mixins disabled") && !log.contains("Version gate overridden"),
                "supported version must pass the gate without rejection or override:\n" + log);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        check(applies(plugin, apply, "MixinWorldTransfer"), "supported version must apply the configured selection");
        check(Files.exists(config), "supported version must write the config");
    }

    private static void verifyVersionRejected(Path config, String original, String reason) throws Exception {
        String version = serverVersion();
        Files.writeString(config, original);
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> check(loadFailure(type, plugin) == null, "unsupported version must not stop startup"));
        check(log.contains(reason), "rejection must give its reason \"" + reason + "\":\n" + log);
        check(log.contains("Early mixins disabled for Hytale " + version), "rejection must name the server version:\n" + log);
        check(log.contains("Refixes supports Hytale 0.8.0-pre.6."), "rejection must name the supported version:\n" + log);
        check((boolean) Class.forName(OPTIONS).getMethod("hasMixinSelection").invoke(null), "rejection must publish an empty selection");
        Method lookup = Class.forName(OPTIONS).getMethod("isMixinEnabled", String.class);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (String mixin : allMixins()) {
            check(!enabled(lookup, mixin) && !applies(plugin, apply, mixin), "unsupported version must leave out " + mixin);
        }
        check(Files.readString(config).equals(original), "unsupported version must not write the config");
    }

    private static void verifyOtherServerVersionsMigration(Path config) throws Exception {
        String[][] cases = {
            {"{\"ApplyUnchangedPatchesOnUnsupportedServerVersion\":true}", "SameMinorVersion",
                "Removed ApplyUnchangedPatchesOnUnsupportedServerVersion=true (old default), OtherServerVersions uses"
                        + " the default SameMinorVersion"},
            {"{\"ApplyUnchangedPatchesOnUnsupportedServerVersion\":false}", "Never",
                "Migrated ApplyUnchangedPatchesOnUnsupportedServerVersion=false to OtherServerVersions=Never"},
            {"{\"ApplyUnchangedPatchesOnUnsupportedServerVersion\":\"allnewerversions\"}", "AllNewerVersions",
                "Migrated ApplyUnchangedPatchesOnUnsupportedServerVersion=\"allnewerversions\" to"
                        + " OtherServerVersions=AllNewerVersions"},
            {"{\"AllowUnsupportedServerVersion\":false}", "SameMinorVersion",
                "Removed AllowUnsupportedServerVersion=false (old default)"},
            {"{\"AllowUnsupportedServerVersion\":true,\"ApplyUnchangedPatchesOnUnsupportedServerVersion\":false}",
                "AllPatches", "Migrated AllowUnsupportedServerVersion=true to OtherServerVersions=AllPatches"},
            {"{\"AllowUnsupportedServerVersion\":true,\"OtherServerVersions\":\"Never\"}", "Never",
                "Ignored AllowUnsupportedServerVersion=true: OtherServerVersions is already set to Never"},
            {"{\"OtherServerVersions\":\"neVER\"}", "Never", null},
            {"{}", "SameMinorVersion", null},
        };
        for (String[] entry : cases) {
            Files.writeString(config, entry[0]);
            Class<?> type = Class.forName(PLUGIN);
            Object plugin = type.getConstructor().newInstance();
            String log = captured(() -> check(loadFailure(type, plugin) == null, "migration must not stop startup"));
            check(entry[2] == null || log.contains("Refixes.json: " + entry[2]),
                    entry[0] + " must log " + entry[2] + ":\n" + log);
            JsonObject saved = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
            check(saved.get("OtherServerVersions").getAsString().equals(entry[1]),
                    entry[0] + " must be saved as " + entry[1] + ": " + saved);
            check(!saved.has("AllowUnsupportedServerVersion")
                            && !saved.has("ApplyUnchangedPatchesOnUnsupportedServerVersion"),
                    "the old keys must be removed: " + saved);
            String written = Files.readString(config);
            Object again = type.getConstructor().newInstance();
            String second = captured(() -> check(loadFailure(type, again) == null, "second start must not fail"));
            check(!second.contains("Refixes.json: "), "a second start must not migrate again:\n" + second);
            check(Files.readString(config).equals(written), "a second start must leave the file unchanged");
        }
    }

    private static void verifyVersionUnchangedUnavailable(Path config, String otherServerVersions) throws Exception {
        if (otherServerVersions != null)
            Files.writeString(config, "{\"OtherServerVersions\":\"" + otherServerVersions + "\"}");
        String version = serverVersion();
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> check(loadFailure(type, plugin) == null, "a failed comparison must not stop startup"));
        check(log.contains("Could not compare the Hytale " + version + " code"), "failed comparison must be reported:\n" + log);
        check(log.contains("\tat "), "failed comparison must print its stack trace:\n" + log);
        check(log.contains("Early mixins disabled for Hytale " + version), "failed comparison must disable early mixins:\n" + log);
        Method lookup = Class.forName(OPTIONS).getMethod("isMixinEnabled", String.class);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (String mixin : allMixins()) {
            check(!enabled(lookup, mixin) && !applies(plugin, apply, mixin), "failed comparison must leave out " + mixin);
        }
        if (otherServerVersions == null) check(!Files.exists(config), "failed comparison must not write the config");
    }

    private static void verifyVersionOverride(Path config, String original, String logged) throws Exception {
        String version = serverVersion();
        Files.writeString(config, original);
        Class<?> type = Class.forName(PLUGIN);
        Object plugin = type.getConstructor().newInstance();
        String log = captured(() -> check(loadFailure(type, plugin) == null, "override must not stop startup"));
        check(log.contains("Version gate overridden for Hytale " + version), "override must be logged:\n" + log);
        check(log.contains(logged), "override start must log " + logged + ":\n" + log);
        Method lookup = Class.forName(OPTIONS).getMethod("isMixinEnabled", String.class);
        Method apply = type.getMethod("shouldApplyMixin", String.class, String.class);
        for (String mixin : Set.of("MixinStoreShutdown", "MixinWorldShutdown")) {
            check(enabled(lookup, mixin) && applies(plugin, apply, mixin), "override must apply configured " + mixin);
        }
        JsonObject saved = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
        check(saved.get("OtherServerVersions").getAsString().equals("AllPatches"),
                "override value must survive serialization: " + saved);
        check(!saved.has("AllowUnsupportedServerVersion")
                        && !saved.has("ApplyUnchangedPatchesOnUnsupportedServerVersion"),
                "the old keys must be removed: " + saved);
        check(saved.getAsJsonObject("Mixins").getAsJsonObject("Recovery").get("ShutdownSaveTimeout").getAsBoolean(),
                "toggle intent must survive serialization");
    }

    private static void verifyVersionOverrideDependency(Path config) throws Exception {
        serverVersion();
        String original = "{\"OtherServerVersions\":\"AllPatches\",\"Mixins\":{\"Correctness\":{\"ParallelTaskLifecycle\":false},"
                + "\"Experimental\":{\"Parallel\":{\"CollectVisible\":true}}}}";
        verifyDependency(config, original, "Mixins.Correctness.ParallelTaskLifecycle");
    }

    private static String serverVersion() {
        String version = System.getProperty(SERVER_VERSION);
        check(version != null, "server version stub missing");
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

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
