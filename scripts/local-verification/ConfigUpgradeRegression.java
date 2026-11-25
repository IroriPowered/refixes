package cc.irori.refixes.config.impl;

import static local.hyinit.verify.RegressionRunner.require;

import cc.irori.refixes.component.TickThrottled;
import cc.irori.refixes.version.HytaleLine;
import cc.irori.refixes.version.HytaleVersions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.util.Config;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.bson.BsonDocument;

public final class ConfigUpgradeRegression {
    private static final String PLUGIN = "cc.irori.refixes.early.RefixesMixinPlugin";
    private static final String NOTE = "[Refixes] Refixes.json: ";
    private static final Path FILE = Path.of("mods/IroriPowered_Refixes/Refixes.json");
    private static final Path BACKUP = Path.of("mods/IroriPowered_Refixes/Refixes.json.bak");

    public static void main(String[] args) throws Exception {
        boolean hytale08 = HytaleLine.nearest(HytaleVersions.running()) == HytaleLine.V08;
        require(!UnknownBlockCleanerConfig.get().getValue(UnknownBlockCleanerConfig.ENABLED), "Destructive cleaner enabled by default");
        require(!TickThrottledCleanupConfig.get().getValue(TickThrottledCleanupConfig.LEGACY_CLEANUP), "Unowned legacy cleanup enabled by default");
        require(DiagnosticsConfig.get().getValue(DiagnosticsConfig.JFR_EVENTS), "JFR events disabled by default");
        Files.createDirectories(FILE.getParent());
        Path fixtures = Path.of(System.getProperty("refixes.fixtures"));
        String own = hytale08 ? "0.8.0-beta.1" : "0.6.1";
        String other = hytale08 ? "0.6.1" : "0.8.0-beta.1";

        String published = Files.readString(fixtures.resolve(own).resolve("Refixes.json"));
        requireQuiet(start(published), "the default Refixes " + own + " config");
        Map<String, JsonElement> added = leaves(read());
        added.entrySet().removeAll(leaves(parse(published)).entrySet());
        if (hytale08) {
            require(added.equals(Map.of("Mixins.Compatibility.ChunkLoader", new JsonPrimitive(true))),
                    "The default Refixes " + own + " config gained or changed " + added);
            require(Files.readString(BACKUP).equals(published), "The file that the first rewrite replaced was not backed up");
        } else {
            require(Files.readString(FILE).equals(published), "The default Refixes " + own + " config was rewritten: " + added);
            require(!Files.exists(BACKUP), "A start that changes nothing wrote a backup");
        }
        String saved = Files.readString(FILE);
        requireQuiet(load(), "the second start of a Refixes " + own + " config");
        require(Files.readString(FILE).equals(saved), "The second start rewrote a Refixes " + own + " config");
        require(!hytale08 || Files.readString(BACKUP).equals(published), "A start that changes nothing replaced the backup");

        JsonObject changed = parse(published);
        changed.addProperty("OtherServerVersions", "AllNewerVersions");
        JsonObject mixins = changed.getAsJsonObject("Mixins");
        mixins.getAsJsonObject("Correctness").addProperty("MountPlugin", false);
        mixins.getAsJsonObject("Optimizations").addProperty("SpawnManagerRecalc", true);
        mixins.getAsJsonObject("Tuning").addProperty("TickWait", false);
        requireQuiet(start(changed.toString()), "a changed Refixes " + own + " config");
        require(leaves(read()).entrySet().containsAll(leaves(changed).entrySet()),
                "A changed Refixes " + own + " config lost a value: " + Files.readString(FILE));
        require(Files.readString(BACKUP).equals(changed.toString()), "A rewrite did not back up the content it replaced");

        JsonObject moved = parse(Files.readString(fixtures.resolve(other).resolve("Refixes.json")));
        moved.getAsJsonObject("Mixins").getAsJsonObject("Correctness").addProperty("WorldTransfer", false);
        requireQuiet(start(moved.toString()), "a Refixes " + other + " config moved to this Hytale line");
        require(leaves(read()).entrySet().containsAll(leaves(moved).entrySet()),
                "A Refixes " + other + " config moved to this Hytale line lost a value: " + Files.readString(FILE));
        if (!hytale08) expect(read().getAsJsonObject("Mixins"), "Correctness.WorldReopenGuard", true);

        requireQuiet(start("{}"), "an empty config");
        Map<String, JsonElement> expected = leaves(read());
        expected.put("Mixins.Correctness.WorldTransfer", new JsonPrimitive(false));
        requireQuiet(start("{\"Mixins\":{\"Correctness\":{\"WorldTransfer\":false}}}"), "a config with one switch");
        require(leaves(read()).equals(expected), "One switch set to false changed another value: " + Files.readString(FILE));

        String unknown = start("{\"Mixins\":{\"Typo\":{\"Foo\":true},\"Correctness\":{\"MountPlugn\":false}}}");
        String named = NOTE + "Ignored unknown settings that stay in the file: Mixins.Typo.Foo, Mixins.Correctness.MountPlugn";
        require(notes(unknown) == 1 && unknown.contains(named), "Unknown settings must be named in one line:\n" + unknown);
        expect(read().getAsJsonObject("Mixins"), "Typo.Foo", true);
        expect(read().getAsJsonObject("Mixins"), "Correctness.MountPlugn", false);
        saved = Files.readString(FILE);
        require(notes(load()) == 1, "The second start must name the unknown settings in one line again");
        require(Files.readString(FILE).equals(saved), "The second start rewrote a config with unknown settings");

        String invalid = start("{\"OtherServerVersions\":\"Sometimes\",\"Mixins\":{\"Correctness\":{\"MountPlugin\":\"no\"},\"Tuning\":7}}");
        for (String line : new String[] {
            "Replaced invalid value OtherServerVersions=\"Sometimes\" with the default",
            "Replaced invalid value Mixins.Correctness.MountPlugin=\"no\" with the default",
            "Replaced invalid value Mixins.Tuning=7 with the default"
        }) require(invalid.contains(NOTE + line), "Missing line: " + line + "\n" + invalid);
        require(read().get("OtherServerVersions").getAsString().equals("SameMinorVersion"), "An invalid OtherServerVersions stayed");
        expect(read().getAsJsonObject("Mixins"), "Correctness.MountPlugin", true);
        expect(read().getAsJsonObject("Mixins"), "Tuning.TickWait", true);

        String old = "{\"Mixins\":{\"Optimizations\":{\"KDTree\":true},\"Experimental\":{\"Parallel\":{\"Steering\":true}},"
                + "\"Crashfixes\":{\"MountPlugin\":true},\"Helpers\":{\"World\":true}},\"HypixelServices\":{\"LiveConfig\":true}}";
        String oldLayout = start(old);
        require(notes(oldLayout) == 1 && oldLayout.contains(NOTE + "This file is from a Refixes older than 0.6.0."),
                "A Refixes 0.5.0 config must be named in one line:\n" + oldLayout);
        require(leaves(read()).entrySet().containsAll(leaves(parse(old)).entrySet()), "A Refixes 0.5.0 config lost a value");
        expect(read().getAsJsonObject("Mixins"), "Correctness.MountPlugin", true);

        require(TickThrottled.CODEC.decode(new BsonDocument(), new ExtraInfo()) != null, "Saved ownership marker no longer decodes");
        Path isolated = Files.createTempDirectory("refixes-runtime-config-");
        Path mixinConfig = isolated.resolve("Refixes.json");
        String mixinJson = "{\"Mixins\":{\"Features\":{\"SharedInstances\":true,\"CustomPolicy\":true}}}";
        Files.writeString(mixinConfig, mixinJson);
        for (int restart = 0; restart < 3; restart++) {
            Config<RefixesConfig> nativeConfig = new Config<>(isolated, "config", RefixesConfig.get().getCodec());
            nativeConfig.load().join();
            nativeConfig.save().join();
            require(Files.readString(mixinConfig).equals(mixinJson), "Runtime config save changed early mixin settings");
            require(Files.isRegularFile(isolated.resolve("config.json")), "Native runtime config file was not saved");
        }
        System.out.println("ConfigUpgradeRegression passed");
    }

    private static String start(String content) throws Exception {
        Files.writeString(FILE, content);
        return load();
    }

    private static String load() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            Class<?> type = Class.forName(PLUGIN);
            type.getMethod("onLoad", String.class).invoke(type.getConstructor().newInstance(), "cc.irori.refixes.early.mixin");
        } finally {
            System.setOut(original);
        }
        String log = captured.toString(StandardCharsets.UTF_8);
        System.out.print(log.lines().filter(line -> !line.contains("[x]") && !line.contains("[ ]")).map(line -> line + "\n").reduce("", String::concat));
        return log;
    }

    private static long notes(String log) {
        return log.lines().filter(line -> line.startsWith(NOTE)).count();
    }

    private static void requireQuiet(String log, String what) {
        require(notes(log) == 0, "Starting with " + what + " logged a note:\n" + log);
    }

    private static JsonObject read() throws Exception {
        return parse(Files.readString(FILE));
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static Map<String, JsonElement> leaves(JsonObject root) {
        Map<String, JsonElement> leaves = new TreeMap<>();
        collect(root, "", leaves);
        return leaves;
    }

    private static void collect(JsonObject object, String prefix, Map<String, JsonElement> leaves) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (entry.getValue().isJsonObject())
                collect(entry.getValue().getAsJsonObject(), prefix + entry.getKey() + ".", leaves);
            else leaves.put(prefix + entry.getKey(), entry.getValue());
        }
    }

    private static void expect(JsonObject mixins, String path, boolean expected) {
        JsonElement current = mixins;
        for (String part : path.split("\\.")) current = current.getAsJsonObject().get(part);
        require(current != null && current.isJsonPrimitive(), "Missing toggle " + path);
        require(current.getAsBoolean() == expected, "Toggle " + path + " expected " + expected + " but was " + current);
    }
}
