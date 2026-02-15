package cc.irori.refixes.util;

import com.hypixel.hytale.logger.HytaleLogger;

public final class Logs {

    private static final String LOGGER_NAME = "Refixes";

    // Private constructor to prevent instantiation
    private Logs() {
    }

    public static HytaleLogger logger() {
        return HytaleLogger.get(LOGGER_NAME);
    }
}
