package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.builtin.instances.removal.InstanceDataResource;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.builtin.portals.ui.PortalDeviceSummonPage;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;
import com.hypixel.hytale.server.core.universe.world.spawn.IndividualSpawnProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Mixin(PortalDeviceSummonPage.class)
public class MixinPortalDeviceSummonPage {

    @Inject(
            method = "spawnReturnPortal",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void refixes$preventDuplicateReturnPortal(World world, PortalWorld portalWorld, UUID uuid, String portalBlockType, CallbackInfoReturnable<CompletableFuture<World>> cir) {
        if (world == null || portalWorld == null) {
            return;
        }
        if (!world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
            return;
        }

        // Reset InstanceDataResource timers so shared worlds behave like a fresh instance
        InstanceDataResource data = world.getChunkStore().getStore().getResource(InstanceDataResource.getResourceType());
        data.setTimeoutTimer(null);
        data.setIdleTimeoutTimer(null);
        data.setWorldTimeoutTimer(null);
        data.setHadPlayer(false);
        data.setRemoving(false);

        // If a spawn point already exists, skip spawning a new return portal
        if (portalWorld.getSpawnPoint() != null) {
            cir.cancel();
            cir.setReturnValue(CompletableFuture.completedFuture(world));
            return;
        }

        // If spawnPoint is missing but spawnProvider is IndividualSpawnProvider,
        // reuse that spawn point and skip spawning a new portal.
        WorldConfig worldConfig = world.getWorldConfig();
        ISpawnProvider spawnProvider = worldConfig.getSpawnProvider();

        if (spawnProvider instanceof IndividualSpawnProvider) {
            Transform spawn = spawnProvider.getSpawnPoint(world, uuid);
            if (spawn != null) {
                portalWorld.setSpawnPoint(spawn);
                cir.cancel();
                cir.setReturnValue(CompletableFuture.completedFuture(world));
            }
        }
    }
}
