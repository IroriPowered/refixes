package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.builtin.mounts.MountPlugin;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MountPlugin.class)
public class MixinMountPlugin {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Inject(
            method =
                    "resetOriginalPlayerMovementSettings(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentAccessor;I)V",
            at = @At("HEAD"),
            cancellable = true)
    private static void refixes$skipForeignRider(
            Ref<EntityStore> playerReference,
            ComponentAccessor<EntityStore> store,
            int mountEntityId,
            CallbackInfo ci) {
        if (!playerReference.isValid()
                || playerReference.getStore() != store.getExternalData().getStore()) {
            refixes$LOGGER.atWarning().log("MountPlugin: Skipping movement reset for an invalid rider reference");
            ci.cancel();
        }
    }
}
