package cc.irori.refixes.util;

import cc.irori.refixes.early.EarlyOptions;

public final class Early {

    private static final String CLASS_NAME = "cc.irori.refixes.early.RefixesEarly";

    private static boolean earlyEnabled = false;

    // Private constructor to prevent instantiation
    private Early() {}

    public static boolean isEnabled() {
        if (earlyEnabled) {
            return true;
        }
        try {
            Class.forName(CLASS_NAME);
            earlyEnabled = true;
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean hasMixins(String... mixins) {
        if (mixins.length == 0) return true;
        if (!isEnabled()) return false;
        for (String mixin : mixins) {
            if (!EarlyOptions.isMixinEnabled(mixin)) return false;
        }
        return true;
    }

    public static void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("Refixes-Early is not installed");
        }
    }
}
