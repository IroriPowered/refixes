package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.PluginBaseShutdownAccess;
import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginListPageManager;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.plugin.PluginState;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PluginManager.class)
public abstract class MixinPluginManagerPreload {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Shadow
    @Final
    private ReentrantReadWriteLock lock;

    @Shadow
    @Final
    private Map<PluginIdentifier, PluginBase> plugins;

    @Shadow
    public PluginListPageManager pluginListPageManager;

    @WrapOperation(
            method = "load(Lcom/hypixel/hytale/server/core/plugin/pending/PendingLoadPlugin;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/plugin/PluginBase;preLoad()Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$discardFailedPreload(
            PluginBase plugin, Operation<CompletableFuture<Void>> original) {
        CompletableFuture<Void> preload;
        try {
            preload = original.call(plugin);
        } catch (Throwable failure) {
            refixes$discard(plugin);
            throw failure;
        }
        if (preload != null) {
            preload.whenComplete((ignored, failure) -> {
                if (failure == null) return;
                refixes$LOGGER.atSevere().withCause(failure).log("Failed to preload plugin %s", plugin.getIdentifier());
                refixes$discard(plugin);
            });
        }
        return preload;
    }

    @Unique
    private void refixes$discard(PluginBase plugin) {
        PluginIdentifier identifier = plugin.getIdentifier();
        lock.writeLock().lock();
        AssetRegistry.ASSET_LOCK.writeLock().lock();
        try {
            if (plugin.getState() != PluginState.NONE || plugins.get(identifier) != plugin) return;
            try {
                ((PluginBaseShutdownAccess) plugin).refixes$shutdown0(false);
            } catch (Throwable failure) {
                refixes$LOGGER.atSevere().withCause(failure).log(
                        "Failed to clean up plugin %s after its preload failed", identifier);
            }
            plugins.remove(identifier);
        } finally {
            AssetRegistry.ASSET_LOCK.writeLock().unlock();
            lock.writeLock().unlock();
        }
        pluginListPageManager.notifyPluginChange(plugins, identifier);
    }
}
