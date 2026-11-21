package cc.irori.refixes.early.util;

import cc.irori.refixes.early.EarlyOptions;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

public final class PatchCounters {

    public static final boolean ENABLED = EarlyOptions.patchCountersEnabled();

    public static final String OFF_MESSAGE =
            "off. Set Mixins.Diagnostics.PatchCounters to true in Refixes.json and restart the server.";

    public static final Counter SPAWN_MANAGER_RECALC = new Counter(
            "SpawnManagerRecalc", "MixinWorldSpawnManager", "recalculations requested", "merged or skipped");
    public static final Counter REPULSION_TICKER =
            new Counter("RepulsionTicker", "MixinRepulsionTicker", "result lists needed", "reused from pool");
    public static final Counter REPULSION_NEIGHBORS = new Counter(
            "RepulsionNeighbors", "MixinRepulsionNeighbors", "neighbor searches", "collected without sorting");
    public static final Counter SOUND_HEADER_VALIDATION = new Counter(
            "SoundHeaderValidation", "MixinSoundFileValidators", "sound info lookups", "read from header only");
    public static final Counter ASSET_PART_SNAPSHOT_SHARING = new Counter(
            "AssetPartSnapshotSharing", "MixinPacketHandlerAssetParts", "asset parts sent", "shared snapshot reused");
    public static final Counter NPC_COMPONENT_TYPE =
            new Counter("NPCComponentType", "MixinNPCComponentType", "NPC type lookups", "answered from cache");
    public static final Counter DEFAULT_MOVEMENT_RESISTANCE = new Counter(
            "DefaultMovementResistance",
            "MixinDefaultMovementResistance",
            "default resistance lookups",
            "shared config returned");
    public static final Counter CODEC_METADATA =
            new Counter("CodecMetadata", "MixinCodecMetadata", "codec contexts created", "metadata maps shared");
    public static final Counter WORLD_HEIGHT_BOUNDS = new Counter(
            "WorldHeightBounds", "MixinChunkTrackerHeightBounds", "height checks", "limited to standard height");
    public static final Counter WORLD_MAP_EXISTING_TERRAIN = new Counter(
            "WorldMapExistingTerrain",
            "MixinWorldMapTrackerMissingTerrain",
            "map image polls",
            "polls held back for missing terrain");
    public static final Counter TICK_WAIT = new Counter("TickWait", "MixinTickSleep", "wait spins", "short sleeps");
    public static final Counter PATHFINDING_LIMITS =
            new Counter("PathfindingLimits", "MixinAStarLimits", "path searches", "searches with lowered limits");
    public static final Counter CHUNK_SAVE_QUEUE_ORDER = new Counter(
            "ChunkSaveQueueOrder", "MixinChunkSaveQueueOrder", "save queue requests", "queued behind waiting columns");
    public static final Counter LIGHT_SAVE_MARKS = new Counter(
            "SkipUnchangedLightSaves",
            "MixinFloodLightSaveMarks",
            "relights",
            "save marks skipped for unchanged light");
    public static final Counter LIGHT_NEUTRAL_BLOCKS = new Counter(
            "LightNeutralBlocks", "MixinFloodLightNeutral", "local relights", "reused after light-neutral changes");

    private static final List<Counter> COUNTERS = List.of(
            SPAWN_MANAGER_RECALC,
            REPULSION_TICKER,
            REPULSION_NEIGHBORS,
            SOUND_HEADER_VALIDATION,
            ASSET_PART_SNAPSHOT_SHARING,
            NPC_COMPONENT_TYPE,
            DEFAULT_MOVEMENT_RESISTANCE,
            CODEC_METADATA,
            WORLD_HEIGHT_BOUNDS,
            WORLD_MAP_EXISTING_TERRAIN,
            TICK_WAIT,
            PATHFINDING_LIMITS,
            CHUNK_SAVE_QUEUE_ORDER,
            LIGHT_SAVE_MARKS,
            LIGHT_NEUTRAL_BLOCKS);

    private PatchCounters() {}

    public static List<Snapshot> snapshot() {
        return COUNTERS.stream().map(Counter::snapshot).toList();
    }

    public record Snapshot(
            String key, boolean enabled, String reachedLabel, long reached, String effectLabel, long effect) {

        public String counts() {
            return reachedLabel + " " + reached + ", " + effectLabel + " " + effect;
        }
    }

    public static final class Counter {

        private final String key;
        private final String mixin;
        private final String reachedLabel;
        private final String effectLabel;
        private final LongAdder reached = new LongAdder();
        private final LongAdder effect = new LongAdder();

        private Counter(String key, String mixin, String reachedLabel, String effectLabel) {
            this.key = key;
            this.mixin = mixin;
            this.reachedLabel = reachedLabel;
            this.effectLabel = effectLabel;
        }

        public void countReached() {
            reached.increment();
        }

        public void countEffect() {
            effect.increment();
        }

        private Snapshot snapshot() {
            long effectCount = effect.sum();
            return new Snapshot(
                    key, EarlyOptions.isMixinEnabled(mixin), reachedLabel, reached.sum(), effectLabel, effectCount);
        }
    }
}
