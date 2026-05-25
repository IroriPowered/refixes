package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.triggervolumes.TriggerVolumesPlugin;
import com.hypixel.hytale.server.core.universe.world.events.RemoveWorldEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Skip cleanup when the world's entity store wasn't fully initialized (init failure path).
@Mixin(TriggerVolumesPlugin.class)
public class MixinTriggerVolumesPlugin {

    @Inject(method = "onWorldRemoved", at = @At("HEAD"), cancellable = true)
    private void refixes$nullGuardOnWorldRemoved(RemoveWorldEvent event, CallbackInfo ci) {
        if (event.getWorld().getEntityStore().getStore() == null) {
            ci.cancel();
        }
    }
}
