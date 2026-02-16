package cc.irori.refixes.util;

import com.hypixel.hytale.logger.HytaleLogger;

public final class Early {

    private static final String CLASS_NAME = "cc.irori.refixes.early.RefixesEarly";
    private static final HytaleLogger LOGGER = Logs.logger();

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

    public static boolean isEnabledLogging(String name) {
        if (!isEnabled()) {
            LOGGER.atWarning().log("Refixes-Early is required to use '%s'!", name);
            return false;
        }
        return true;
    }

    public static void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("Refixes-Early is not installed");
        }
    }
}
