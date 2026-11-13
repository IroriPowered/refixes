package cc.irori.refixes.early.util;

import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.util.Config;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class PluginConfigWrites {
    private static final HytaleLogger LOGGER = Logs.logger();
    private static final long WARN_SECONDS = 30L;
    private static final Map<PluginIdentifier, CompletableFuture<Void>> PENDING = new ConcurrentHashMap<>();

    private PluginConfigWrites() {}

    public interface Access {
        CompletableFuture<Void> refixes$pendingWrite();
    }

    public static void retire(PluginIdentifier identifier, List<Config<?>> configs) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (Config<?> config : configs) {
            if (!(config instanceof Access access)) continue;
            CompletableFuture<Void> write = access.refixes$pendingWrite();
            if (write != null && !write.isDone()) writes.add(write.handle((ignored, failure) -> null));
        }
        if (writes.isEmpty()) return;
        CompletableFuture<Void> pending = PENDING.compute(identifier, (key, previous) -> {
            if (previous != null) writes.add(previous);
            return CompletableFuture.allOf(writes.toArray(new CompletableFuture<?>[0]));
        });
        pending.whenComplete((ignored, failure) -> PENDING.remove(identifier, pending));
    }

    public static CompletableFuture<Void> preLoad(PluginBase plugin, Supplier<CompletableFuture<Void>> preLoad) {
        PluginIdentifier identifier = plugin.getIdentifier();
        CompletableFuture<Void> pending = PENDING.get(identifier);
        if (pending == null || pending.isDone()) return preLoad.get();
        LOGGER.atInfo().log("Loading plugin %s after its previous instance finishes writing its config", identifier);
        CompletableFuture.delayedExecutor(WARN_SECONDS, TimeUnit.SECONDS).execute(() -> {
            if (!pending.isDone()) {
                LOGGER.atWarning().log(
                        "Plugin %s is still waiting for config writes of its previous instance after %d seconds",
                        identifier, WARN_SECONDS);
            }
        });
        return pending.thenCompose(ignored -> {
                    if (HytaleServer.get().isShuttingDown()
                            || PluginManager.get().getPlugin(identifier) != plugin) {
                        return CompletableFuture.<Void>failedFuture(new IllegalStateException("Plugin " + identifier
                                + " was stopped while waiting for config writes of its previous instance"));
                    }
                    CompletableFuture<Void> loaded = preLoad.get();
                    return loaded == null ? CompletableFuture.<Void>completedFuture(null) : loaded;
                })
                .whenComplete((ignored, failure) -> {
                    if (failure != null)
                        LOGGER.atWarning().withCause(failure).log(
                                "Plugin %s did not load after waiting for config writes of its previous instance",
                                identifier);
                });
    }
}
