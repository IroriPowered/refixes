package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginClassLoader;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.hypixel.hytale.server.core.plugin.pending.PendingLoadJavaPlugin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PluginManager.class)
public abstract class MixinPluginManagerLoaderCache {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Shadow
    @Final
    private ReentrantReadWriteLock lock;

    @Shadow
    @Final
    private Map<PluginIdentifier, PluginBase> plugins;

    @Shadow
    @Final
    private Map<Path, PluginClassLoader> classLoaders;

    @WrapOperation(
            method = "findPluginInDirectory",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/plugin/PluginManager;loadPendingJavaPlugin(Ljava/nio/file/Path;)Lcom/hypixel/hytale/server/core/plugin/pending/PendingLoadJavaPlugin;"),
            require = 1,
            allow = 1)
    private PendingLoadJavaPlugin refixes$replaceUnusedLoader(
            PluginManager manager, Path file, Operation<PendingLoadJavaPlugin> original) {
        PluginClassLoader cached = classLoaders.get(file);
        if (cached != null && !refixes$isInUse(cached) && classLoaders.remove(file, cached)) {
            try {
                cached.close();
            } catch (IOException failure) {
                refixes$LOGGER.atWarning().withCause(failure).log(
                        "Failed to close the unused class loader of %s", file);
            }
        }
        return original.call(manager, file);
    }

    @Redirect(
            method = "loadManifest",
            at = @At(value = "INVOKE", target = "Ljava/net/URL;openStream()Ljava/io/InputStream;"),
            require = 1,
            allow = 1)
    private static InputStream refixes$readManifestUncached(URL manifest) throws IOException {
        URLConnection connection = manifest.openConnection();
        connection.setUseCaches(false);
        return connection.getInputStream();
    }

    @Unique
    private boolean refixes$isInUse(PluginClassLoader loader) {
        lock.readLock().lock();
        try {
            for (PluginBase plugin : plugins.values()) {
                if (plugin instanceof JavaPlugin javaPlugin && javaPlugin.getClassLoader() == loader) return true;
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }
}
