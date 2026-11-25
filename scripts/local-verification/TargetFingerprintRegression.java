package cc.irori.refixes.early;

import static local.hyinit.verify.RegressionRunner.require;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

public final class TargetFingerprintRegression {

    private static final Class<?> PLUGIN = RefixesMixinPlugin.class;
    private static final String FINGERPRINTS = "META-INF/hyinit/fingerprints.json";

    public static void main(String[] args) throws Exception {
        verifyBaselines();
        verifySwitches();
        verifyHyinitChanges();
        System.out.println("PASS unchanged code gate");
    }

    private static void verifyBaselines() throws Exception {
        @SuppressWarnings("unchecked")
        List<String> targets = (List<String>) field("TARGET_SERVER_VERSIONS").get(null);
        Path jar = Path.of(PLUGIN.getProtectionDomain().getCodeSource().getLocation().toURI());
        JsonObject servers;
        try (JarFile file = new JarFile(jar.toFile())) {
            require(file.getEntry(FINGERPRINTS) != null, "the Refixes JAR has no " + FINGERPRINTS);
            try (var reader = new InputStreamReader(file.getInputStream(file.getEntry(FINGERPRINTS)), StandardCharsets.UTF_8)) {
                servers = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("servers");
            }
        }
        require(servers.keySet().equals(Set.copyOf(targets)),
                "the Hyinit fingerprints must be recorded for exactly " + targets + ", found " + servers.keySet());
        for (String target : targets)
            require(!servers.getAsJsonObject(target).getAsJsonObject("mixins").isEmpty(),
                    "no Mixins in the Hyinit fingerprints for Hytale " + target);
    }

    private static void verifyHyinitChanges() throws Exception {
        List<Map<String, Object>> targets = List.of(
                changedTarget("cc.irori.refixes.early.mixin.MixinWorldConfig", "method", "changed", "0.7.7"),
                changedTarget("cc.irori.refixes.early.mixin.MixinMountPlugin", "at", "missing", "0.7.7"),
                changedTarget("cc.irori.refixes.early.mixin.MixinMountPlugin", "call", "missing", null),
                changedTarget("cc.irori.refixes.early.mixin.MixinTickSleep", "method", "changed", "0.7.7"),
                changedTarget("other.plugin.mixin.MixinWorldConfig", "method", "changed", "0.7.7"));
        Map<String, List<String>> changes = hyinitChanges(targets, List.of(fingerprintCheck("compared", "checked", "0.7.7")));
        require(changes.keySet().equals(Set.of("MixinWorldConfig", "MixinMountPlugin")),
                "Hyinit changed targets must map to the selected Refixes mixins only: " + changes);
        require(changes.get("MixinMountPlugin").equals(List.of(
                        "at com.hypixel.Target.member()V missing since Hytale 0.7.7", "call com.hypixel.Target.member()V missing")),
                "a Hyinit changed target must name its kind, member, status and baseline: " + changes);
        require(hyinitChanges(List.of(), List.of(fingerprintCheck("compared", "checked", "0.7.7.1"))).isEmpty(),
                "a comparison against a hotfix build of the target without changes must report none");
        Map<String, String> other = new HashMap<>(fingerprintCheck("compared", "checked", "0.7.7"));
        other.put("pluginId", "Other:Plugin");
        List<List<Map<String, String>>> refused = List.of(
                List.of(),
                List.of(other),
                List.of(fingerprintCheck("failed", "checked", null)),
                List.of(fingerprintCheck("notShipped", "checked", null)),
                List.of(fingerprintCheck("compared", "failed", "0.7.7")),
                List.of(fingerprintCheck("compared", "checked", "0.7.6")),
                List.of(fingerprintCheck("compared", "checked", "0.7.7"), fingerprintCheck("failed", "checked", null)));
        for (List<Map<String, String>> checks : refused) {
            try {
                hyinitChanges(List.of(), checks);
            } catch (IllegalStateException expected) {
                continue;
            }
            throw new AssertionError("a comparison Hyinit did not finish must not count as unchanged: " + checks);
        }
    }
    
    private static Map<String, List<String>> hyinitChanges(
            List<Map<String, Object>> targets, List<Map<String, String>> checks) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("hyinitVersion", "0.5.0");
        snapshot.put("changedTargets", targets);
        snapshot.put("fingerprintChecks", checks);
        return RefixesMixinPlugin.hyinitChanges(
                snapshot, "0.7.7", "cc.irori.refixes.early.mixin.", Set.of("MixinWorldConfig", "MixinMountPlugin"));
    }
    
    private static Map<String, String> fingerprintCheck(String status, String usedMembers, String baseline) {
        Map<String, String> entry = new HashMap<>();
        entry.put("jar", "refixes.jar");
        entry.put("pluginId", "IroriPowered:Refixes");
        entry.put("status", status);
        entry.put("usedMembers", usedMembers);
        entry.put("baseline", baseline);
        entry.put("detail", null);
        return entry;
    }

    private static Map<String, Object> changedTarget(String mixin, String kind, String status, String baseline) {
        Map<String, Object> target = new HashMap<>();
        target.put("jar", "refixes.jar");
        target.put("mixin", mixin);
        target.put("class", "com.hypixel.Target");
        target.put("member", "member()V");
        target.put("kind", kind);
        target.put("status", status);
        target.put("baseline", baseline);
        return target;
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
        require(always.keySet().equals(defaultWorldData),
                "on another minor version unchanged code must skip exactly the enabled world data switches: "
                        + always.keySet());

        Map<?, ?> partial = (Map<?, ?>) skipped.invoke(
                null, config, Set.of(), Map.of("MixinQuicConnectionAccess", List.of("changed")), false);
        require(extra(partial, always).equals(Set.of("Mixins.Correctness.QuicCloseRetention")),
                "one changed mixin must skip exactly its switch: " + partial);
        Set<?> selected = (Set<?>) selection.invoke(null, config, Set.of(), partial.keySet());
        for (String mixin : List.of("MixinQuicChannelClose", "MixinQuicConnectionAccess", "MixinQuicTimeoutAccess"))
            require(!selected.contains(mixin), "a skipped switch must leave out all of its mixins: " + mixin);
        require(selected.contains("MixinMountPlugin"), "an unchanged switch must stay applied");
        for (String mixin : List.of("MixinSaveCompletionWorld", "MixinChunkSaveQueueOrder", "MixinBlockSectionSaveMarks"))
            require(!selected.contains(mixin), "a world data switch must be left out: " + mixin);

        Map<?, ?> cascade =
                (Map<?, ?>) skipped.invoke(null, config, Set.of(), Map.of("MixinSetupConfigPacing", List.of("changed")), false);
        require(extra(cascade, always)
                        .equals(Set.of("Mixins.Correctness.SetupPacing", "Mixins.Correctness.OutgoingQueueLimits")),
                "a switch that needs a skipped switch must be skipped too: " + cascade);

        Set<String> watchdog = Set.of("MixinTickingThreadAccess");
        Map<?, ?> base = (Map<?, ?>) skipped.invoke(null, config, watchdog, Map.of(), false);
        require(base.containsKey("Mixins.Correctness.WorldOwnership") && base.containsKey("Mixins.Compatibility.Watchdog"),
                "world ownership and the watchdog must be skipped on another version: " + base.keySet());
        selected = (Set<?>) selection.invoke(null, config, watchdog, base.keySet());
        Set<Object> kept = new HashSet<>();
        for (Object entry : toggles) {
            if (!(boolean) enabled.invoke(null, config, entry) || base.containsKey(path.invoke(null, entry))) continue;
            Set<?> entryMixins = (Set<?>) toggleMixins.invoke(null, entry, watchdog);
            require(selected.containsAll(entryMixins), "a kept switch must keep all of its mixins: " + path.invoke(null, entry));
            kept.addAll(entryMixins);
        }
        for (Object entry : toggles) {
            if (!base.containsKey(path.invoke(null, entry))) continue;
            for (Object mixin : (Set<?>) toggleMixins.invoke(null, entry, watchdog)) {
                if (!kept.contains(mixin))
                    require(!selected.contains(mixin), "a mixin only skipped switches use must be left out: " + mixin);
            }
        }
        require(!selected.contains("MixinTickingThreadAccess"), "the accessor of skipped switches must be left out");

        Map<?, ?> shared = (Map<?, ?>)
                skipped.invoke(null, config, Set.of(), Map.of("MixinTickingThreadRunAccess", List.of("changed")), false);
        require(extra(shared, always).equals(Set.of("Mixins.Correctness.TickingThreadStart")),
                "a changed shared accessor must skip every switch that uses it: " + shared);
        selected = (Set<?>) selection.invoke(null, config, Set.of(), shared.keySet());
        require(!selected.contains("MixinTickingThreadRunAccess"), "a changed shared accessor must be left out");

        JsonObject recovery = new JsonObject();
        JsonObject switches = new JsonObject();
        JsonObject category = new JsonObject();
        category.addProperty("TickSurvival", true);
        switches.add("Recovery", category);
        recovery.add("Mixins", switches);
        Map<?, ?> same = (Map<?, ?>) skipped.invoke(null, recovery, Set.of(), Map.of(), false);
        require(extra(same, always).equals(Set.of("Mixins.Recovery.TickSurvival")),
                "an enabled Recovery switch must be skipped on another version even with unchanged code: " + same);

        Map<?, ?> sameMinor = (Map<?, ?>) skipped.invoke(null, config, watchdog, Map.of(), true);
        require(extra(sameMinor, Map.of()).equals(union(defaultDestructive, "Mixins.Compatibility.Watchdog")),
                "on the same minor version only destructive world data switches and the watchdog runtime Recovery"
                        + " switch may be skipped: " + sameMinor);
        selected = (Set<?>) selection.invoke(null, config, watchdog, sameMinor.keySet());
        for (String additive : defaultWorldData) {
            if (defaultDestructive.contains(additive)) continue;
            for (Object mixin : (Set<?>) toggleMixins.invoke(null, toggle.invoke(null, additive), watchdog))
                require(selected.contains(mixin), "an unchanged additive world data switch must apply on the same minor"
                        + " version: " + additive + " " + mixin);
        }
        Map<?, ?> sameMinorCleaner = (Map<?, ?>)
                skipped.invoke(null, config, Set.of("MixinBlockComponentSectionAccess"), Map.of(), true);
        require(sameMinorCleaner.keySet().equals(union(defaultDestructive, "Mixins.Compatibility.UnknownBlockCleaner")),
                "the block cleaner runtime Recovery switch stays target-only: " + sameMinorCleaner);
        Map<?, ?> sameMinorChanged = (Map<?, ?>)
                skipped.invoke(null, config, Set.of(), Map.of("MixinChunkSaveQueueOrder", List.of("changed")), true);
        require(sameMinorChanged.keySet().equals(union(defaultDestructive, "Mixins.Correctness.ChunkSaveQueueOrder")),
                "on the same minor version an additive world data switch with changed code must be skipped: "
                        + sameMinorChanged);
        Map<?, ?> sameMinorRecovery = (Map<?, ?>) skipped.invoke(null, recovery, Set.of(), Map.of(), true);
        require(sameMinorRecovery.keySet().equals(union(defaultDestructive, "Mixins.Recovery.TickSurvival")),
                "Recovery switches stay target-only on the same minor version: " + sameMinorRecovery);
    }

    private static Set<String> union(Set<String> set, String element) {
        Set<String> union = new HashSet<>(set);
        union.add(element);
        return union;
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
}
