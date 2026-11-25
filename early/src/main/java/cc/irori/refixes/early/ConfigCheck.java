package cc.irori.refixes.early;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ConfigCheck {

    private static final String MIXINS = "Mixins";
    private static final List<String> OLD_SECTIONS = List.of("Crashfixes", "Helpers");

    private ConfigCheck() {}

    static List<String> check(JsonObject config, Set<String> switches) {
        String setting = RefixesMixinPlugin.OTHER_SERVER_VERSIONS;
        List<String> log = new ArrayList<>();
        JsonElement value = config.get(setting);
        if (value != null) {
            String accepted = otherServerVersions(value);
            if (accepted == null) {
                config.remove(setting);
                log.add(invalid(setting, value));
            } else {
                config.addProperty(setting, accepted);
            }
        }
        JsonElement mixins = config.get(MIXINS);
        if (mixins != null && !mixins.isJsonObject()) {
            config.remove(MIXINS);
            log.add(invalid(MIXINS, mixins));
        }
        List<String> unknown = new ArrayList<>();
        for (String key : config.keySet()) {
            if (!key.equals(setting) && !key.equals(MIXINS)) unknown.add(key);
        }
        boolean oldLayout = false;
        if (config.get(MIXINS) instanceof JsonObject sections) {
            oldLayout = OLD_SECTIONS.stream().anyMatch(sections::has);
            collect(sections, MIXINS, switches, unknown, log);
        }
        if (oldLayout) {
            log.add("This file is from a Refixes older than 0.6.0. Switches under their old names are not read and use"
                    + " their defaults. Start the server once with Refixes 0.6.1 to convert the file, or delete it");
        } else if (!unknown.isEmpty()) {
            log.add("Ignored unknown settings that stay in the file: " + String.join(", ", unknown));
        }
        return log;
    }

    private static void collect(
            JsonObject object, String path, Set<String> switches, List<String> unknown, List<String> log) {
        List<String> invalid = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String child = path + "." + entry.getKey();
            JsonElement value = entry.getValue();
            boolean section = switches.stream().anyMatch(known -> known.startsWith(child + "."));
            if (switches.contains(child) ? !isBoolean(value) : section && !value.isJsonObject()) {
                invalid.add(entry.getKey());
                log.add(invalid(child, value));
            } else if (section) {
                collect(value.getAsJsonObject(), child, switches, unknown, log);
            } else if (!switches.contains(child)) {
                leaves(value, child, unknown);
            }
        }
        invalid.forEach(object::remove);
    }

    private static void leaves(JsonElement value, String path, List<String> unknown) {
        if (!value.isJsonObject() || value.getAsJsonObject().isEmpty()) {
            unknown.add(path);
            return;
        }
        for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet())
            leaves(entry.getValue(), path + "." + entry.getKey(), unknown);
    }

    private static String invalid(String path, JsonElement value) {
        return "Replaced invalid value " + path + "=" + value + " with the default";
    }

    private static String otherServerVersions(JsonElement value) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
        for (String accepted : RefixesMixinPlugin.OTHER_SERVER_VERSIONS_VALUES) {
            if (accepted.equalsIgnoreCase(value.getAsString().trim())) return accepted;
        }
        return null;
    }

    private static boolean isBoolean(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
    }
}
