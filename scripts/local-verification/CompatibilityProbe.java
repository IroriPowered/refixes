package local.hyinit.verify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;

public final class CompatibilityProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PLAN = "verification-plan.json";

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("default", "all").contains(args[2])) {
            throw new IllegalArgumentException("Expected: <hyinit.jar> <refixes.jar> <default|all>");
        }
        List<JsonObject> entries = new ArrayList<>();
        collect(Path.of(args[0]), "_hyinit.mixins.json", false, entries);
        collect(Path.of(args[1]), "refixes.mixins.json", args[2].equals("all"), entries);
        JsonObject plan = new JsonObject();
        plan.addProperty("mode", args[2]);
        plan.add("mixins", JSON.toJsonTree(entries));
        Files.writeString(Path.of(PLAN), JSON.toJson(plan));
        System.out.println("Prepared " + entries.size() + " mixin-target pairs for " + args[2]);
    }

    private static void collect(Path jarPath, String configName, boolean all, List<JsonObject> entries)
            throws Exception {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JsonObject config;
            try (InputStream input = jar.getInputStream(requireEntry(jar, configName))) {
                config = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject();
            }
            String prefix = config.get("package").getAsString();
            IMixinConfigPlugin plugin = null;
            if (config.has("plugin")) {
                Class<?> pluginType = Class.forName(config.get("plugin").getAsString());
                if (all) writeAllEnabledConfig(pluginType);
                plugin = (IMixinConfigPlugin) pluginType.getConstructor().newInstance();
                plugin.onLoad(prefix);
            }
            for (var item : config.getAsJsonArray("mixins")) {
                String mixin = prefix + "." + item.getAsString();
                ClassNode node;
                try (InputStream input = jar.getInputStream(requireEntry(jar, mixin.replace('.', '/') + ".class"))) {
                    node = classNode(input.readAllBytes());
                }
                Set<String> targets = new LinkedHashSet<>();
                for (AnnotationNode annotation : annotations(node.visibleAnnotations, node.invisibleAnnotations)) {
                    if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")) continue;
                    Object values = annotationValue(annotation, "value");
                    if (values instanceof List<?> list) {
                        for (Object value : list) targets.add(((Type) value).getClassName());
                    }
                    Object names = annotationValue(annotation, "targets");
                    if (names instanceof List<?> list) {
                        for (Object name : list) targets.add(name.toString().replace('/', '.'));
                    }
                }
                if (targets.isEmpty()) throw new AssertionError("No @Mixin targets in " + mixin);
                for (String target : targets) {
                    JsonObject entry = new JsonObject();
                    entry.addProperty("mixin", mixin);
                    entry.addProperty("target", target);
                    entry.addProperty("active", plugin == null || plugin.shouldApplyMixin(target, mixin));
                    entries.add(entry);
                }
            }
        }
    }

    private static void writeAllEnabledConfig(Class<?> pluginType) throws Exception {
        var togglesField = pluginType.getDeclaredField("TOGGLES");
        togglesField.setAccessible(true);
        var configField = pluginType.getDeclaredField("CONFIG_PATH");
        configField.setAccessible(true);
        JsonObject root = new JsonObject();
        int toggles = 0;
        for (Object toggle : (Iterable<?>) togglesField.get(null)) {
            Method pathMethod = toggle.getClass().getDeclaredMethod("jsonPath");
            Method enabledMethod = toggle.getClass().getDeclaredMethod("enabledWhen");
            pathMethod.setAccessible(true);
            enabledMethod.setAccessible(true);
            String[] path = (String[]) pathMethod.invoke(toggle);
            JsonObject current = root;
            for (int i = 0; i < path.length - 1; i++) {
                if (!current.has(path[i])) current.add(path[i], new JsonObject());
                current = current.getAsJsonObject(path[i]);
            }
            current.addProperty(path[path.length - 1], (Boolean) enabledMethod.invoke(toggle));
            toggles++;
        }
        if (toggles == 0) throw new AssertionError("No optional mixin toggles discovered");
        Path path = (Path) configField.get(null);
        Files.createDirectories(path.getParent());
        Files.writeString(path, JSON.toJson(root));
    }

    public static void verify() {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (!loader.getClass().getName().equals("cc.irori.hyinit.mixin.HyinitClassLoader")) {
                throw new AssertionError("Probe did not enter the real Hyinit classloader: " + loader);
            }
            JsonObject plan = JsonParser.parseString(Files.readString(Path.of(PLAN))).getAsJsonObject();
            Map<String, Set<String>> expectedByTarget = new LinkedHashMap<>();
            Set<String> targets = new LinkedHashSet<>();
            int active = 0;
            for (var element : plan.getAsJsonArray("mixins")) {
                JsonObject entry = element.getAsJsonObject();
                String target = entry.get("target").getAsString();
                targets.add(target);
                if (entry.get("active").getAsBoolean()) {
                    expectedByTarget.computeIfAbsent(target, unused -> new LinkedHashSet<>())
                            .add(entry.get("mixin").getAsString());
                    active++;
                }
            }
            JsonArray transformed = new JsonArray();
            for (String target : targets) {
                System.out.println("[LocalVerify] Loading " + target);
                Class<?> type = Class.forName(target, false, loader);
                if (type.getClassLoader() != loader) {
                    throw new AssertionError("Target bypassed Hyinit transformation: " + target);
                }
                type.getDeclaredMethods();
                type.getDeclaredConstructors();
                Set<String> expected = expectedByTarget.getOrDefault(target, Set.of());
                if (!expected.isEmpty()) {
                    Path exported = Path.of(".mixin.out", "class", target.replace('.', '/') + ".class");
                    if (!Files.isRegularFile(exported)) {
                        throw new AssertionError("No transformed bytecode exported for " + target);
                    }
                    ClassNode targetNode = classNode(Files.readAllBytes(exported));
                    Set<String> merged = mergedMixins(targetNode);
                    for (String mixin : expected) {
                        if (!merged.contains(mixin) && !interfacesApplied(loader, mixin, targetNode)) {
                            throw new AssertionError("Active mixin did not merge into " + target + ": " + mixin);
                        }
                    }
                }
                transformed.add(target);
            }
            verifyEarlyPluginLoading(loader);
            SharedInstanceStorageRegression.assertSaveSectionCallbacks();
            JsonObject result = new JsonObject();
            result.addProperty("status", "passed");
            result.addProperty("mode", plan.get("mode").getAsString());
            result.addProperty("activeMixinTargetPairs", active);
            result.add("transformedTargets", transformed);
            result.addProperty("earlyPluginLoading", "registered dummy transformer removed by real loader");
            result.addProperty("sharedInstanceStorage", "saveSection callbacks fire once inline; retry after backend throw");
            result.addProperty("strictInjectionCounts", Boolean.getBoolean("mixin.debug.countInjections"));
            if (!result.get("strictInjectionCounts").getAsBoolean()) {
                throw new AssertionError("Strict injection counting is required");
            }
            Files.writeString(Path.of("verification-result.json"), JSON.toJson(result));
            System.out.println("[LocalVerify] PASS " + targets.size() + " targets, " + active + " active mixin-target pairs");
            if (!Boolean.getBoolean("local.verify.fullBoot") && !Boolean.getBoolean("local.verify.setupRegression")) {
                System.exit(0);
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                Files.writeString(Path.of("verification-failure.txt"), failure.toString());
            } catch (Exception ignored) {
            }
            System.exit(1);
        }
    }

    public static void verifyAfterSetup(com.hypixel.hytale.server.core.HytaleServer server) {
        if (!Boolean.getBoolean("local.verify.setupRegression")) return;
        try {
            if (server.isShuttingDown()) {
                throw new AssertionError("Server was already shutting down at the end of setup");
            }
            var plugins = server.getPluginManager().getPlugins().stream()
                    .filter(plugin -> plugin.getIdentifier().toString().equals("IroriPowered:Refixes"))
                    .toList();
            if (plugins.size() != 1) {
                throw new AssertionError("Expected exactly one Refixes runtime plugin at setup: " + plugins);
            }
            SectionBackoffDeadlockRepro.assertPatched(SectionBackoffDeadlockRepro.setupPhaseWorld());
            SharedInstanceStorageRegression.runWithRegistry(SectionBackoffDeadlockRepro.fixtureStore());
            JsonObject result = new JsonObject();
            result.addProperty("status", "setup-verified");
            result.addProperty("refixesRuntimePlugin", plugins.getFirst().getIdentifier().toString());
            result.addProperty("pluginState", plugins.getFirst().getState().toString());
            result.addProperty("sectionBackoffDeadlock", "completed exceptionally on transformed ChunkStore");
            result.addProperty("sharedInstanceUuidLessHolder", "stable UUID + transform after failed first save");
            Files.writeString(Path.of("setup-result.json"), JSON.toJson(result));
            System.out.println("[LocalVerify] Setup-phase regressions PASS");
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                Files.writeString(Path.of("verification-failure.txt"), failure.toString());
            } catch (Exception ignored) {
            }
            System.exit(1);
        }
    }

    public static void observeBoot(com.hypixel.hytale.server.core.HytaleServer server) {
        if (!Boolean.getBoolean("local.verify.fullBoot")) return;
        try {
            if (!server.isBooted() || server.isShuttingDown()) {
                throw new AssertionError("Server constructor returned without a successful boot");
            }
            var plugins = server.getPluginManager().getPlugins().stream()
                    .filter(plugin -> plugin.getIdentifier().toString().equals("IroriPowered:Refixes"))
                    .toList();
            if (plugins.size() != 1
                    || plugins.getFirst().getState() != com.hypixel.hytale.server.core.plugin.PluginState.ENABLED) {
                throw new AssertionError("Expected exactly one enabled Refixes runtime plugin: " + plugins);
            }
            JsonObject result = new JsonObject();
            result.addProperty("status", "booted");
            result.addProperty("refixesRuntimePlugin", plugins.getFirst().getIdentifier().toString());
            result.addProperty("pluginState", plugins.getFirst().getState().toString());
            var world = com.hypixel.hytale.server.core.universe.Universe.get().getDefaultWorld();
            SectionBackoffDeadlockRepro.assertPatched(world);
            FutureTask<Void> storageRegression = new FutureTask<>(() -> {
                SharedInstanceStorageRegression.runOnStoreThread(world.getChunkStore().getStore());
                return null;
            });
            world.execute(storageRegression);
            storageRegression.get(10, TimeUnit.SECONDS);
            result.addProperty("sharedInstanceUuidLessHolder", "stable UUID + transform after failed first save");
            result.addProperty("sectionBackoffDeadlock", "completed exceptionally on transformed ChunkStore");
            Files.writeString(Path.of("boot-result.json"), JSON.toJson(result));
            System.out.println("[LocalVerify] Full boot and Refixes plugin enablement PASS");
        } catch (Throwable failure) {
            failure.printStackTrace();
            server.shutdownServer(com.hypixel.hytale.server.core.ShutdownReason.CRASH);
        }
    }

    private static void verifyEarlyPluginLoading(ClassLoader serverLoader) throws Exception {
        String dummy = "cc.irori.hyinit.dummy.DummyClassTransformer";
        Class<?> earlyLoader = serverLoader.loadClass("com.hypixel.hytale.plugin.early.EarlyPluginLoader");
        earlyLoader.getMethod("loadEarlyPlugins", String[].class)
                .invoke(null, (Object) new String[] {"--accept-early-plugins"});
        ClassLoader plugins = (ClassLoader) earlyLoader.getMethod("getPluginClassLoader").invoke(null);
        if (plugins == null) throw new AssertionError("Early plugin loader did not discover the verification JARs");
        Class<?> transformer = serverLoader.loadClass("com.hypixel.hytale.plugin.early.ClassTransformer");
        boolean registered = false;
        for (Object service : java.util.ServiceLoader.load(transformer, plugins)) {
            if (service.getClass().getName().equals(dummy)) registered = true;
        }
        if (!registered) throw new AssertionError("Positive control: Hyinit dummy transformer was not registered");
        for (Object service : (List<?>) earlyLoader.getMethod("getTransformers").invoke(null)) {
            if (service.getClass().getName().equals(dummy)) {
                throw new AssertionError("Hyinit's dummy transformer survived real early-plugin loading");
            }
        }
        System.out.println("[LocalVerify] Early-plugin transformer removal PASS");
    }


    private static Set<String> mergedMixins(ClassNode node) {
        Set<String> result = new HashSet<>();
        for (MethodNode method : node.methods) {
            for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")) {
                    Object mixin = annotationValue(annotation, "mixin");
                    if (mixin != null) result.add(mixin.toString());
                }
            }
        }
        return result;
    }

    private static boolean interfacesApplied(ClassLoader loader, String mixin, ClassNode target) throws Exception {
        try (InputStream in = loader.getResourceAsStream(mixin.replace('.', '/') + ".class")) {
            if (in == null) throw new AssertionError("Mixin class bytes not found: " + mixin);
            List<String> declared = classNode(in.readAllBytes()).interfaces;
            return declared != null && !declared.isEmpty() && target.interfaces.containsAll(declared);
        }
    }


    private static ClassNode classNode(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static JarEntry requireEntry(JarFile jar, String name) {
        JarEntry entry = jar.getJarEntry(name);
        if (entry == null) throw new AssertionError("Missing " + name + " in " + jar.getName());
        return entry;
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
            }
        }
        return null;
    }
}
