package cc.irori.refixes.version;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum HytaleLine {
    V07("0.7.7", "<0.8.0-pre.1", "cc.irori.refixes.version.HytaleV07", 0, 0, 0, 0),
    V08("0.8.0-pre.6", ">=0.8.0-pre.1 || >=0.9.0-0", "cc.irori.refixes.version.HytaleV08", 0, 8, 0, 1);

    private static final Pattern VERSION = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:-pre\\.(\\d+))?(?:\\.\\d+)*");

    private final String target;
    private final String range;
    private final String adapter;
    private final int[] first;

    HytaleLine(String target, String range, String adapter, int... first) {
        this.target = target;
        this.range = range;
        this.adapter = adapter;
        this.first = first;
    }

    public String target() {
        return target;
    }

    public String range() {
        return range;
    }

    public String adapter() {
        return adapter;
    }

    public boolean covers(String version) {
        return parse(version) != null && HytaleVersions.matches(range, version);
    }

    public static HytaleLine nearest(String version) {
        HytaleLine nearest = values()[0];
        int[] parsed = parse(version);
        if (parsed == null) return nearest;
        for (HytaleLine line : values()) {
            if (Arrays.compare(parsed, line.first) >= 0) nearest = line;
        }
        return nearest;
    }

    private static int[] parse(String version) {
        if (version == null) return null;
        Matcher matcher = VERSION.matcher(version.trim());
        if (!matcher.matches()) return null;
        int[] parsed = new int[4];
        try {
            for (int i = 0; i < 3; i++) parsed[i] = Integer.parseInt(matcher.group(i + 1));
            parsed[3] = matcher.group(4) == null ? Integer.MAX_VALUE : Integer.parseInt(matcher.group(4));
        } catch (NumberFormatException tooLarge) {
            return null;
        }
        return parsed;
    }
}
