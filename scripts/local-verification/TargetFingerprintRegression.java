package cc.irori.refixes.early;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class TargetFingerprintRegression {

    private static final Class<?> PLUGIN = RefixesMixinPlugin.class;

    public static void main(String[] args) throws Exception {
        TargetFingerprints.Baseline baseline = TargetFingerprints.baseline();
        URL main = ClassLoader.getSystemResource("com/hypixel/hytale/Main.class");
        check(main != null && main.openConnection() instanceof JarURLConnection, "Hytale server JAR missing");
        JarURLConnection connection = (JarURLConnection) main.openConnection();
        String version = Objects.requireNonNullElse(
                connection.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION), "unknown");
        String target = (String) field("TARGET_SERVER_VERSION").get(null);
        check(ServerVersion.relation(target, baseline.serverVersion()) == ServerVersion.Relation.TARGET,
                "fingerprints were made for Hytale " + baseline.serverVersion() + ", not for the target " + target);
        Set<String> mixins = new HashSet<>();
        for (TargetFingerprints.Fingerprint fingerprint : baseline.fingerprints())
            mixins.add(fingerprint.member().mixin());
        try (JarFile server = new JarFile(connection.getJarFileURL().getPath())) {
            TargetFingerprints.ClassSource source = name -> read(server, name);
            TargetFingerprints.Result result = TargetFingerprints.compare(baseline, mixins, source);
            check(result.classes() > 80, "too few Hytale classes fingerprinted: " + result.classes());
            if (ServerVersion.relation(target, version) == ServerVersion.Relation.TARGET)
                check(result.changes().isEmpty(), "Hytale " + version + " is the target " + target
                        + ", its code must match the fingerprints: " + result.changes());
            if (result.changes().isEmpty()) {
                verifyDetection(baseline, mixins, server);
            } else {
                System.out.println("Hytale " + version + " differs from Hytale " + baseline.serverVersion()
                        + " in the code of " + result.changes().size()
                        + " fingerprinted mixins and runtime plugin classes, detection checks skipped");
            }
        }
        verifySwitches();
        verifyVersionOrder();
        System.out.println("PASS target fingerprints for Hytale " + baseline.serverVersion() + " on Hytale " + version
                + ", " + baseline.fingerprints().size() + " members of " + mixins.size() + " mixins");
    }

    private static void verifyDetection(TargetFingerprints.Baseline baseline, Set<String> mixins, JarFile server)
            throws IOException {
        TargetFingerprints.Member injected = null;
        TargetFingerprints.Member target = null;
        for (TargetFingerprints.Fingerprint fingerprint : baseline.fingerprints()) {
            TargetFingerprints.Member member = fingerprint.member();
            if (injected == null
                    && member.role() == TargetFingerprints.Role.INJECT
                    && !member.name().equals(TargetFingerprints.ANY)
                    && !member.desc().equals(TargetFingerprints.ANY)) injected = member;
        }
        check(injected != null, "no injected method with a descriptor in the fingerprints");
        for (TargetFingerprints.Fingerprint fingerprint : baseline.fingerprints()) {
            TargetFingerprints.Member member = fingerprint.member();
            if (member.role() == TargetFingerprints.Role.TARGET
                    && !member.owner().equals(injected.owner())
                    && !member.mixin().equals(injected.mixin())) {
                target = member;
                break;
            }
        }
        check(target != null, "no second target class in the fingerprints");
        TargetFingerprints.Member changed = injected;
        String removed = target.owner();
        TargetFingerprints.Result result = TargetFingerprints.compare(baseline, mixins, name -> {
            if (name.equals(removed)) return null;
            ClassNode node = read(server, name);
            if (node != null && name.equals(changed.owner())) {
                for (MethodNode method : node.methods) {
                    if (method.name.equals(changed.name()) && method.desc.equals(changed.desc()))
                        method.instructions.insert(new InsnNode(Opcodes.NOP));
                }
            }
            return node;
        });
        List<String> injectedChanges = result.changes().get(injected.mixin());
        check(injectedChanges != null
                        && injectedChanges.stream()
                                .anyMatch(line -> line.startsWith("injected method") && line.endsWith(" changed")),
                "a changed injected method must be reported for " + injected.mixin() + ": " + result.changes());
        List<String> targetChanges = result.changes().get(target.mixin());
        check(targetChanges != null
                        && targetChanges.stream()
                                .anyMatch(line -> line.startsWith("target class") && line.endsWith(" missing")),
                "a missing target class must be reported for " + target.mixin() + ": " + result.changes());
        for (String mixin : result.changes().keySet()) {
            boolean touches = false;
            for (TargetFingerprints.Fingerprint fingerprint : baseline.fingerprints()) {
                TargetFingerprints.Member member = fingerprint.member();
                if (member.mixin().equals(mixin)
                        && (member.owner().equals(removed) || member.owner().equals(changed.owner()))) touches = true;
            }
            boolean throughPlugin = result.changes().get(mixin).stream()
                    .allMatch(line -> line.startsWith(TargetFingerprints.Role.PLUGIN.label + " "));
            check(touches || throughPlugin,
                    mixin + " was reported although it uses neither changed class: " + result.changes().get(mixin));
        }
        verifyLambda(baseline, mixins, server);
    }

    private static void verifyLambda(TargetFingerprints.Baseline baseline, Set<String> mixins, JarFile server)
            throws IOException {
        TargetFingerprints.Member injected = null;
        String lambda = null;
        for (TargetFingerprints.Fingerprint fingerprint : baseline.fingerprints()) {
            TargetFingerprints.Member member = fingerprint.member();
            if (member.role() != TargetFingerprints.Role.INJECT || member.name().equals(TargetFingerprints.ANY))
                continue;
            ClassNode node = read(server, member.owner());
            for (MethodNode method : node.methods) {
                if (method.name.startsWith("lambda$" + member.name() + "$")) {
                    injected = member;
                    lambda = method.name + method.desc;
                    break;
                }
            }
            if (injected != null) break;
        }
        check(injected != null, "no injected method with a lambda in the fingerprints");
        TargetFingerprints.Member owner = injected;
        String changedLambda = lambda;
        TargetFingerprints.Result result = TargetFingerprints.compare(baseline, mixins, name -> {
            ClassNode node = read(server, name);
            if (node != null && name.equals(owner.owner())) {
                for (MethodNode method : node.methods) {
                    if ((method.name + method.desc).equals(changedLambda))
                        method.instructions.insert(new InsnNode(Opcodes.NOP));
                }
            }
            return node;
        });
        List<String> changes = result.changes().get(injected.mixin());
        check(changes != null && changes.stream().anyMatch(line -> line.contains(" and its lambdas changed")),
                "a changed lambda of an injected method must be reported for " + injected.mixin() + ": "
                        + result.changes());
    }

    private static void verifySwitches() throws Exception {
        Method skipped = method("skippedSwitches", JsonObject.class, Set.class, Map.class, boolean.class);
        Method selection = method("selection", JsonObject.class, Set.class, Set.class);
        Class<?> toggleType = Class.forName(PLUGIN.getName() + "$MixinToggle");
        Method path = method("path", toggleType);
        Method enabled = method("toggleEnabled", JsonObject.class, toggleType);
        Method toggleMixins = method("toggleMixins", toggleType, Set.class);
        Method toggle = method("toggle", String.class);
        List<?> toggles = (List<?>) field("TOGGLES").get(null);
        JsonObject config = new JsonObject();
        Map<?, ?> always = (Map<?, ?>) skipped.invoke(null, config, Set.of(), Map.of(), false);
        Set<String> defaultWorldData = new HashSet<>();
        Set<String> defaultDestructive = new HashSet<>();
        for (Map.Entry<?, ?> worldData : ((Map<?, ?>) field("WORLD_DATA_SWITCHES").get(null)).entrySet()) {
            Object entry = toggle.invoke(null, worldData.getKey());
            if (!(boolean) enabled.invoke(null, config, entry)
                    || ((Set<?>) toggleMixins.invoke(null, entry, Set.of())).isEmpty()) continue;
            defaultWorldData.add((String) worldData.getKey());
            Method destructive = worldData.getValue().getClass().getDeclaredMethod("destructive");
            destructive.setAccessible(true);
            if ((boolean) destructive.invoke(worldData.getValue())) defaultDestructive.add((String) worldData.getKey());
        }
        check(always.keySet().equals(defaultWorldData),
                "on another minor version unchanged code must skip exactly the enabled world data switches: "
                        + always.keySet());

        Map<?, ?> partial = (Map<?, ?>) skipped.invoke(
                null, config, Set.of(), Map.of("MixinQuicConnectionAccess", List.of("changed")), false);
        check(extra(partial, always).equals(Set.of("Mixins.Correctness.QuicCloseRetention")),
                "one changed mixin must skip exactly its switch: " + partial);
        Set<?> selected = (Set<?>) selection.invoke(null, config, Set.of(), partial.keySet());
        for (String mixin : List.of("MixinQuicChannelClose", "MixinQuicConnectionAccess", "MixinQuicTimeoutAccess"))
            check(!selected.contains(mixin), "a skipped switch must leave out all of its mixins: " + mixin);
        check(selected.contains("MixinMountPlugin"), "an unchanged switch must stay applied");
        for (String mixin : List.of("MixinSaveCompletionWorld", "MixinChunkSaveQueueOrder", "MixinBlockSectionSaveMarks"))
            check(!selected.contains(mixin), "a world data switch must be left out: " + mixin);

        Map<?, ?> cascade =
                (Map<?, ?>) skipped.invoke(null, config, Set.of(), Map.of("MixinSetupConfigPacing", List.of("changed")), false);
        check(extra(cascade, always)
                        .equals(Set.of("Mixins.Correctness.SetupPacing", "Mixins.Correctness.OutgoingQueueLimits")),
                "a switch that needs a skipped switch must be skipped too: " + cascade);

        Set<String> watchdog = Set.of("MixinTickingThreadAccess");
        Map<?, ?> base = (Map<?, ?>) skipped.invoke(null, config, watchdog, Map.of(), false);
        check(base.containsKey("Mixins.Correctness.WorldOwnership") && base.containsKey("Mixins.Compatibility.Watchdog"),
                "world ownership and the watchdog must be skipped on another version: " + base.keySet());
        selected = (Set<?>) selection.invoke(null, config, watchdog, base.keySet());
        Set<Object> kept = new HashSet<>();
        for (Object entry : toggles) {
            if (!(boolean) enabled.invoke(null, config, entry) || base.containsKey(path.invoke(null, entry))) continue;
            Set<?> entryMixins = (Set<?>) toggleMixins.invoke(null, entry, watchdog);
            check(selected.containsAll(entryMixins), "a kept switch must keep all of its mixins: " + path.invoke(null, entry));
            kept.addAll(entryMixins);
        }
        for (Object entry : toggles) {
            if (!base.containsKey(path.invoke(null, entry))) continue;
            for (Object mixin : (Set<?>) toggleMixins.invoke(null, entry, watchdog)) {
                if (!kept.contains(mixin))
                    check(!selected.contains(mixin), "a mixin only skipped switches use must be left out: " + mixin);
            }
        }
        check(!selected.contains("MixinTickingThreadAccess"), "the accessor of skipped switches must be left out");

        Map<?, ?> shared = (Map<?, ?>)
                skipped.invoke(null, config, Set.of(), Map.of("MixinTickingThreadRunAccess", List.of("changed")), false);
        check(extra(shared, always).equals(Set.of("Mixins.Correctness.TickingThreadStart")),
                "a changed shared accessor must skip every switch that uses it: " + shared);
        selected = (Set<?>) selection.invoke(null, config, Set.of(), shared.keySet());
        check(!selected.contains("MixinTickingThreadRunAccess"), "a changed shared accessor must be left out");

        JsonObject recovery = new JsonObject();
        JsonObject switches = new JsonObject();
        JsonObject category = new JsonObject();
        category.addProperty("TickSurvival", true);
        switches.add("Recovery", category);
        recovery.add("Mixins", switches);
        Map<?, ?> same = (Map<?, ?>) skipped.invoke(null, recovery, Set.of(), Map.of(), false);
        check(extra(same, always).equals(Set.of("Mixins.Recovery.TickSurvival")),
                "an enabled Recovery switch must be skipped on another version even with unchanged code: " + same);

        Map<?, ?> sameMinor = (Map<?, ?>) skipped.invoke(null, config, watchdog, Map.of(), true);
        check(extra(sameMinor, Map.of()).equals(union(defaultDestructive, "Mixins.Compatibility.Watchdog")),
                "on the same minor version only destructive world data switches and the watchdog runtime Recovery"
                        + " switch may be skipped: " + sameMinor);
        selected = (Set<?>) selection.invoke(null, config, watchdog, sameMinor.keySet());
        for (String additive : defaultWorldData) {
            if (defaultDestructive.contains(additive)) continue;
            for (Object mixin : (Set<?>) toggleMixins.invoke(null, toggle.invoke(null, additive), watchdog))
                check(selected.contains(mixin), "an unchanged additive world data switch must apply on the same minor"
                        + " version: " + additive + " " + mixin);
        }
        Map<?, ?> sameMinorCleaner = (Map<?, ?>)
                skipped.invoke(null, config, Set.of("MixinBlockComponentSectionAccess"), Map.of(), true);
        check(sameMinorCleaner.keySet().equals(union(defaultDestructive, "Mixins.Compatibility.UnknownBlockCleaner")),
                "the block cleaner runtime Recovery switch stays target-only: " + sameMinorCleaner);
        Map<?, ?> sameMinorChanged = (Map<?, ?>)
                skipped.invoke(null, config, Set.of(), Map.of("MixinChunkSaveQueueOrder", List.of("changed")), true);
        check(sameMinorChanged.keySet().equals(union(defaultDestructive, "Mixins.Correctness.ChunkSaveQueueOrder")),
                "on the same minor version an additive world data switch with changed code must be skipped: "
                        + sameMinorChanged);
        Map<?, ?> sameMinorRecovery = (Map<?, ?>) skipped.invoke(null, recovery, Set.of(), Map.of(), true);
        check(sameMinorRecovery.keySet().equals(union(defaultDestructive, "Mixins.Recovery.TickSurvival")),
                "Recovery switches stay target-only on the same minor version: " + sameMinorRecovery);
    }

    private static Set<String> union(Set<String> set, String element) {
        Set<String> union = new HashSet<>(set);
        union.add(element);
        return union;
    }

    private static void verifyVersionOrder() {
        Object[][] cases = {
            {"0.7.7", "0.7.7", ServerVersion.Relation.TARGET},
            {"0.7.7", "0.7.7.1", ServerVersion.Relation.TARGET},
            {"0.7.7", "0.7.6", ServerVersion.Relation.OLDER},
            {"0.7.7", "0.7.5", ServerVersion.Relation.OLDER},
            {"0.7.7", "0.6.9", ServerVersion.Relation.OLDER},
            {"0.7.7", "0.5.10", ServerVersion.Relation.OLDER},
            {"0.7.7", "0.7.0-pre.5.2", ServerVersion.Relation.OLDER},
            {"0.7.7", "0.7.7-pre.1", ServerVersion.Relation.OLDER},
            {"0.7.7", "0.7.8", ServerVersion.Relation.NEWER_SAME_MINOR},
            {"0.7.7", "0.7.8.1", ServerVersion.Relation.NEWER_SAME_MINOR},
            {"0.7.7", "0.7.70", ServerVersion.Relation.NEWER_SAME_MINOR},
            {"0.7.7", "0.8.0-pre.5", ServerVersion.Relation.NEWER_MINOR},
            {"0.7.7", "0.8.0-pre.6", ServerVersion.Relation.NEWER_MINOR},
            {"0.7.7", "0.8.0", ServerVersion.Relation.NEWER_MINOR},
            {"0.7.7", "1.0.0", ServerVersion.Relation.NEWER_MINOR},
            {"0.8.0-pre.6", "0.8.0-pre.6", ServerVersion.Relation.TARGET},
            {"0.8.0-pre.6", "0.8.0-pre.6.1", ServerVersion.Relation.TARGET},
            {"0.8.0-pre.6", "0.8.0-pre.5", ServerVersion.Relation.OLDER},
            {"0.8.0-pre.6", "0.8.0-pre.5.2", ServerVersion.Relation.OLDER},
            {"0.8.0-pre.6", "0.8.0-pre.1", ServerVersion.Relation.OLDER},
            {"0.8.0-pre.6", "0.7.7", ServerVersion.Relation.OLDER},
            {"0.8.0-pre.6", "0.6.0-pre.13.1", ServerVersion.Relation.OLDER},
            {"0.8.0-pre.6", "0.8.0-pre.7", ServerVersion.Relation.NEWER_SAME_MINOR},
            {"0.8.0-pre.6", "0.8.0-pre.10", ServerVersion.Relation.NEWER_SAME_MINOR},
            {"0.8.0-pre.6", "0.8.0", ServerVersion.Relation.NEWER_SAME_MINOR},
            {"0.8.0-pre.6", "0.8.0.1", ServerVersion.Relation.NEWER_SAME_MINOR},
            {"0.8.0-pre.6", "0.8.1", ServerVersion.Relation.NEWER_SAME_MINOR},
            {"0.8.0-pre.6", "0.9.0-pre.1", ServerVersion.Relation.NEWER_MINOR},
            {"0.8.0-pre.6", "0.9.0", ServerVersion.Relation.NEWER_MINOR},
            {"0.7.7", null, ServerVersion.Relation.UNKNOWN},
            {"0.7.7", "unknown", ServerVersion.Relation.UNKNOWN},
            {"0.7.7", "0.7", ServerVersion.Relation.UNKNOWN},
            {"0.7.7", "0.7.8-beta", ServerVersion.Relation.UNKNOWN},
            {"0.7.7", "0.7.99999999999", ServerVersion.Relation.UNKNOWN},
            {"0.7.7", " 0.7.7", ServerVersion.Relation.UNKNOWN},
        };
        for (Object[] entry : cases) {
            ServerVersion.Relation relation = ServerVersion.relation((String) entry[0], (String) entry[1]);
            check(relation == entry[2], "target " + entry[0] + " running " + entry[1] + " must be " + entry[2]
                    + ", got " + relation);
        }
        check(ServerVersion.minor("0.8.0-pre.6").equals("0.8"), "the minor version of 0.8.0-pre.6 is 0.8");
        check(ServerVersion.minor("0.7.7").equals("0.7"), "the minor version of 0.7.7 is 0.7");
    }

    private static Method method(String name, Class<?>... types) throws NoSuchMethodException {
        Method method = PLUGIN.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = PLUGIN.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Set<Object> extra(Map<?, ?> skipped, Map<?, ?> baseline) {
        Set<Object> extra = new HashSet<>(skipped.keySet());
        extra.removeAll(baseline.keySet());
        return extra;
    }

    private static ClassNode read(JarFile jar, String internalName) throws IOException {
        JarEntry entry = jar.getJarEntry(internalName + ".class");
        if (entry == null) return null;
        try (InputStream input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input.readAllBytes()).accept(node, TargetFingerprints.READER_FLAGS);
            return node;
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
