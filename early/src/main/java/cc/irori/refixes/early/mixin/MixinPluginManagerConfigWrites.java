package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.PluginConfigWrites;
import com.hypixel.hytale.server.core.plugin.PluginBase;
import com.hypixel.hytale.server.core.plugin.PluginManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PluginManager.class)
public abstract class MixinPluginManagerConfigWrites {
    @WrapOperation(
            method = "load(Lcom/hypixel/hytale/server/core/plugin/pending/PendingLoadPlugin;)Z",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/plugin/PluginBase;preLoad()Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<Void> refixes$preLoadAfterRetiredWrites(
            PluginBase plugin, Operation<CompletableFuture<Void>> original) {
        return PluginConfigWrites.preLoad(plugin, () -> original.call(plugin));
    }
}
