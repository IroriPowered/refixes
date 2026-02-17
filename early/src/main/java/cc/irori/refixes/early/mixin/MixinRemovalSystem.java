package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.SharedInstanceConstants;
import com.hypixel.hytale.builtin.instances.removal.InstanceDataResource;
import com.hypixel.hytale.builtin.instances.removal.RemovalSystem;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RemovalSystem.class)
public class MixinRemovalSystem {

    @Inject(method = "shouldRemoveWorld", at = @At("HEAD"), cancellable = true)
    private static void refixes$overrideWorldRemoval(Store<ChunkStore> store, CallbackInfoReturnable<Boolean> cir) {
        World world = store.getExternalData().getWorld();
        if (!world.getName().startsWith(SharedInstanceConstants.SHARED_INSTANCE_PREFIX)) {
            return;
        }

        PortalWorld portalWorld = world.getEntityStore().getStore().getResource(PortalWorld.getResourceType());
        InstanceDataResource instanceData =
                world.getChunkStore().getStore().getResource(InstanceDataResource.getResourceType());

        if (((MixinPortalWorldAccessor) portalWorld).getWorldRemovalCondition() == null) {
            return;
        }

        if (instanceData.getTimeoutTimer() == null) {
            double seconds = portalWorld.getRemainingSeconds(world);
            PortalWorld.setRemainingSeconds(world, seconds);
        }

        if (portalWorld.getRemainingSeconds(world) > 0.0d) {
            cir.cancel();
            cir.setReturnValue(false);
        }
    }
}
