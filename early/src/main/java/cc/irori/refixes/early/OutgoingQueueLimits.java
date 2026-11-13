package cc.irori.refixes.early;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record OutgoingQueueLimits(long maxBytes, int maxPackets) {

    public static final OutgoingQueueLimits DEFAULT = new OutgoingQueueLimits(67_108_864L, 65_536);

    public OutgoingQueueLimits {
        if (maxBytes < 0L)
            throw new IllegalArgumentException("Early.OutgoingQueueMaxBytes must be a non-negative integer");
        if (maxPackets < 0)
            throw new IllegalArgumentException("Early.OutgoingQueueMaxPackets must be a non-negative integer");
        if (maxBytes == 0L) maxBytes = Long.MAX_VALUE;
        if (maxPackets == 0) maxPackets = Integer.MAX_VALUE;
    }

    public static OutgoingQueueLimits fromConfig(JsonObject config) {
        JsonElement value = config.get("Early");
        if (value == null) return DEFAULT;
        if (!value.isJsonObject())
            throw new IllegalStateException("Invalid Refixes config: Early must be a JSON object");
        JsonObject early = value.getAsJsonObject();
        long bytes = nonNegativeInteger(early, "OutgoingQueueMaxBytes", DEFAULT.maxBytes, Long.MAX_VALUE);
        int packets = (int) nonNegativeInteger(early, "OutgoingQueueMaxPackets", DEFAULT.maxPackets, Integer.MAX_VALUE);
        return new OutgoingQueueLimits(bytes, packets);
    }

    private static long nonNegativeInteger(JsonObject early, String key, long fallback, long maximum) {
        JsonElement value = early.get(key);
        if (value == null) return fallback;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            try {
                long number = Long.parseLong(value.getAsString());
                if (number >= 0L && number <= maximum) return number;
            } catch (NumberFormatException ignored) {
            }
        }
        throw new IllegalStateException("Invalid Refixes config: Early." + key
                + " must be 0 (no limit) or a positive integer not greater than " + maximum);
    }
}
