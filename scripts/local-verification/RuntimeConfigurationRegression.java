package cc.irori.refixes.config.impl;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.util.Config;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RuntimeConfigurationRegression {
    public static void main(String[] args) throws Exception {
        RefixesConfig config = RefixesConfig.get();
        BuilderCodec<RefixesConfig> codec = config.getCodec();
        var encoded = codec.encode(config, new ExtraInfo());
        require(encoded.containsKey("Early"), "Early parameter section missing");
        require(encoded.containsKey("Runtime"), "Runtime section missing");
        for (String removed : new String[] {"Listeners", "Systems", "Services", "ChunkLoader", "Watchdog", "Tools"}) {
            require(!encoded.containsKey(removed), "Retired root section remains: " + removed);
        }
        var runtime = encoded.getDocument("Runtime");
        for (String category : new String[] {
            "Correctness", "Tuning", "Features", "Recovery", "Compatibility", "Diagnostics"
        }) {
            require(runtime.containsKey(category), "Runtime category missing: " + category);
        }

        require(!runtime.containsKey("Experimental"), "Retired runtime experimental parameters remain");
        for (String key : new String[] {"BackpressureMaxOutboundBytes", "BackpressureGraceMs"})
            require(!encoded.getDocument("Early").containsKey(key), "Retired early parameter remains: " + key);

        require(!AiTickThrottlerConfig.get().getValue(AiTickThrottlerConfig.ENABLED), "AI tuning enabled by default");
        require(ChunkLoaderConfig.get().getValue(ChunkLoaderConfig.ENABLED), "Chunkloader disabled by default");
        require(PerPlayerHotRadiusConfig.get().getValue(PerPlayerHotRadiusConfig.ENABLED), "Radius tuning disabled by default");
        require(!UnknownBlockCleanerConfig.get().getValue(UnknownBlockCleanerConfig.ENABLED), "Destructive cleaner enabled by default");
        require(CompatibilityConfig.get().getValue(CompatibilityConfig.EXISTING_SHARED_INSTANCES), "Existing shared storage maintenance disabled");
        require(TickThrottledCleanupConfig.get().getValue(TickThrottledCleanupConfig.CLEANUP_FROZEN_ENTITIES), "Owned saved marker cleanup disabled");
        require(!TickThrottledCleanupConfig.get().getValue(TickThrottledCleanupConfig.LEGACY_CLEANUP), "Unowned legacy cleanup enabled");
        EntityDespawnTimerConfig despawn = EntityDespawnTimerConfig.get();
        require(!despawn.shouldRegisterSystem(), "Inert despawn system registered by default");
        for (var action : java.util.List.of(EntityDespawnTimerConfig.ADD_TIMER_WHEN_MISSING, EntityDespawnTimerConfig.OVERRIDE_EXISTING_TIMERS)) {
            despawn.setValue(action, true);
            require(despawn.shouldRegisterSystem(), "Requested despawn action did not enable the system");
            despawn.setValue(action, false);
        }
        require(WatchdogRecoveryConfig.get().getValue(WatchdogRecoveryConfig.SHUTDOWN_ON_DEFAULT_WORLD_CRASH), "Default world crash shutdown disabled");
        for (var action : java.util.List.of(WatchdogRecoveryConfig.AUTO_RESTART_WORLDS, WatchdogRecoveryConfig.HALT_ON_SHUTDOWN_TIMEOUT)) {
            require(!WatchdogRecoveryConfig.get().getValue(action), "Watchdog recovery enabled by default: " + action.name());
        }
        var marker = cc.irori.refixes.component.TickThrottled.CODEC.decode(new org.bson.BsonDocument(), new ExtraInfo());
        require(marker != null, "Saved ownership marker no longer decodes");

        Path data = Files.createTempDirectory("refixes-runtime-config-");
        Path mixinConfig = data.resolve("Refixes.json");
        String mixinJson = "{\"Mixins\":{\"Features\":{\"SharedInstances\":true,\"CustomPolicy\":true}}}";
        Files.writeString(mixinConfig, mixinJson);
        for (int restart = 0; restart < 3; restart++) {
            Config<RefixesConfig> nativeConfig = new Config<>(data, "config", codec);
            nativeConfig.load().join();
            nativeConfig.save().join();
            require(Files.readString(mixinConfig).equals(mixinJson), "Runtime config save changed early mixin settings");
            require(Files.isRegularFile(data.resolve("config.json")), "Native runtime config file was not saved");
        }
        System.out.println("RuntimeConfigurationRegression passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
