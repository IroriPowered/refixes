package cc.irori.refixes.early;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ServerVersion {

    enum Relation {
        TARGET,
        OLDER,
        NEWER_SAME_MINOR,
        NEWER_MINOR,
        UNKNOWN
    }

    private static final Pattern FORMAT = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:-pre\\.(\\d+))?((?:\\.\\d+)*)");
    private static final int BUILD = 4;

    private ServerVersion() {}

    static Relation relation(String target, String running) {
        int[] built = parse(target);
        int[] current = parse(running);
        if (built == null || current == null) return Relation.UNKNOWN;
        if (Arrays.equals(current, 0, BUILD, built, 0, BUILD) && Arrays.compare(current, built) >= 0)
            return Relation.TARGET;
        if (Arrays.compare(current, built) < 0) return Relation.OLDER;
        return current[0] == built[0] && current[1] == built[1] ? Relation.NEWER_SAME_MINOR : Relation.NEWER_MINOR;
    }

    static String minor(String version) {
        int[] parsed = parse(version);
        return parsed == null ? version : parsed[0] + "." + parsed[1];
    }

    private static int[] parse(String version) {
        if (version == null) return null;
        Matcher matcher = FORMAT.matcher(version);
        if (!matcher.matches()) return null;
        String[] hotfix = matcher.group(5).isEmpty()
                ? new String[0]
                : matcher.group(5).substring(1).split("\\.");
        int[] parsed = new int[BUILD + hotfix.length];
        try {
            for (int i = 0; i < 3; i++) parsed[i] = Integer.parseInt(matcher.group(i + 1));
            parsed[3] = matcher.group(4) == null ? Integer.MAX_VALUE : Integer.parseInt(matcher.group(4));
            for (int i = 0; i < hotfix.length; i++) parsed[BUILD + i] = Integer.parseInt(hotfix[i]);
        } catch (NumberFormatException tooLarge) {
            return null;
        }
        return parsed;
    }
}
