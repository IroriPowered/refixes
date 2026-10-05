package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.builtin.instances.removal.InstanceDataResource;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.builtin.portals.ui.PortalDeviceSummonPage;
import com.hypixel.hytale.logger.sentry.SkipSentryException;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.spawn.IndividualSpawnProvider;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PortalDeviceSummonPage.class)
public class MixinPortalDeviceSummonPage {

    @Inject(
            method =
                    "spawnReturnPortal(Lcom/hypixel/hytale/server/core/universe/world/World;Lcom/hypixel/hytale/builtin/portals/resources/PortalWorld;Ljava/util/UUID;Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",
            at = @At("HEAD"),
            cancellable = true)
    private static void refixes$preventDuplicateReturnPortal(
            World world,
            PortalWorld portalWorld,
            UUID uuid,
            String portalBlockType,
            CallbackInfoReturnable<CompletableFuture<World>> cir) {
        if (world == null || portalWorld == null) {
            return;
        }
        if (!world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
            return;
        }

        InstanceDataResource data =
                world.getChunkStore().getStore().getResource(InstanceDataResource.getResourceType());
        if (data.isRemoving()) {
            cir.setReturnValue(CompletableFuture.failedFuture(
                    new SkipSentryException("Shared portal world is being removed: " + world.getName(), null)));
            return;
        }

        // Reset only instance timers; do not revive a world with removal already scheduled.
        data.setTimeoutTimer(null);
        data.setIdleTimeoutTimer(null);
        data.setWorldTimeoutTimer(null);
        data.setHadPlayer(false);

        // If a spawn point already exists, skip spawning a new return portal
        if (portalWorld.getSpawnPoint() != null) {
            cir.setReturnValue(CompletableFuture.completedFuture(world));
            return;
        }

        // If spawnPoint is missing but spawnProvider is IndividualSpawnProvider,
        // reuse that spawn point and skip spawning a new portal.
        WorldConfig worldConfig = world.getWorldConfig();
        ISpawnProvider spawnProvider = worldConfig.getSpawnProvider();

        if (spawnProvider instanceof IndividualSpawnProvider) {
            cir.setReturnValue(spawnProvider
                    .getSpawnPointAsync(world, uuid)
                    .thenApplyAsync(
                            spawn -> {
                                if (spawn == null) {
                                    throw new SkipSentryException(
                                            "No valid spawn location for portal world " + world.getName(), null);
                                }
                                if (data.isRemoving()) {
                                    throw new SkipSentryException(
                                            "Shared portal world is being removed: " + world.getName(), null);
                                }
                                portalWorld.setSpawnPoint(spawn);
                                return world;
                            },
                            world));
        }
    }
}
