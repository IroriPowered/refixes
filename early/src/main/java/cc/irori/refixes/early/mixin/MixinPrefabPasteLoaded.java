package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditSession;
import com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabEditSessionManager;
import com.hypixel.hytale.builtin.buildertools.prefabeditor.PrefabLoadingState;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PrefabEditSessionManager.class)
public abstract class MixinPrefabPasteLoaded {

    @Shadow
    private void notifyProgress(Consumer<PrefabLoadingState> progressCallback, PrefabLoadingState loadingState) {}

    @Shadow
    public abstract CompletableFuture<Void> cleanupCancelledSession(UUID playerUuid, String worldName);

    @Inject(
            method =
                    "pastePrefab(Lcom/hypixel/hytale/builtin/buildertools/prefabeditor/PrefabEditSessionManager$PendingPaste;Lcom/hypixel/hytale/server/core/util/PrefabUtil$PasteRegion;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/builtin/buildertools/prefabeditor/PrefabEditSession;ILcom/hypixel/hytale/builtin/buildertools/prefabeditor/PrefabLoadingState;Ljava/util/function/Consumer;Ljava/util/UUID;)Lcom/hypixel/hytale/server/core/universe/world/World;",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            allow = 1)
    private void refixes$skipPartialPaste(
            @Coerce Object placement,
            PrefabUtil.PasteRegion pasteRegion,
            World world,
            PrefabEditSession editSession,
            int pasteFlags,
            PrefabLoadingState loadingState,
            Consumer<PrefabLoadingState> progressCallback,
            UUID playerUuid,
            CallbackInfoReturnable<World> cir) {
        if (pasteRegion.isFullyLoaded()) return;
        Path path = ((MixinPrefabPendingPasteAccess) placement).refixes$getPath();
        Logs.logger()
                .atWarning()
                .log(
                        "Skipping prefab %s, its paste region reaches sections that are outside the world or failed to load",
                        path);
        String details = path.getFileName() + ": part of it is outside the world or could not be loaded";
        if (loadingState != null) {
            loadingState.addError("server.commands.editprefab.error.pasteFailed", details);
            notifyProgress(progressCallback, loadingState);
        }
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (playerRef != null) {
            playerRef.sendMessage(Message.translation("server.commands.editprefab.error.pasteFailed")
                    .param("details", details));
        }
        cir.setReturnValue(world);
    }

    @WrapOperation(
            method =
                    "pastePrefabs(Ljava/util/List;Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/builtin/buildertools/prefabeditor/PrefabEditSession;ILcom/hypixel/hytale/builtin/buildertools/prefabeditor/PrefabLoadingState;Ljava/util/function/Consumer;Ljava/util/UUID;)Ljava/util/concurrent/CompletableFuture;",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Ljava/util/concurrent/CompletableFuture;thenApplyAsync(Ljava/util/function/Function;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 1,
            allow = 1)
    private CompletableFuture<World> refixes$cleanupEmptySession(
            CompletableFuture<World> chain,
            Function<World, World> finish,
            Executor executor,
            Operation<CompletableFuture<World>> original,
            @Local(argsOnly = true) PrefabEditSession editSession,
            @Local(argsOnly = true) UUID playerUuid) {
        return original.call(
                chain,
                (Function<World, World>) world -> {
                    if (world != null && editSession.getLoadedPrefabMetadata().isEmpty()) {
                        cleanupCancelledSession(playerUuid, world.getName());
                        return null;
                    }
                    return finish.apply(world);
                },
                executor);
    }
}
