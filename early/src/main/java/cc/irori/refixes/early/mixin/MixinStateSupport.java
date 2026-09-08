package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StateSupport.class)
public abstract class MixinStateSupport {

    @Shadow
    protected Set<Ref<EntityStore>> interactablePlayers;

    @Inject(method = "update", at = @At("HEAD"))
    private void refixes$removeInvalidPlayers(ComponentAccessor<EntityStore> componentAccessor, CallbackInfo ci) {
        if (interactablePlayers == null) {
            return;
        }
        var store = componentAccessor.getExternalData().getStore();
        for (var iterator = interactablePlayers.iterator(); iterator.hasNext(); ) {
            Ref<EntityStore> ref = iterator.next();
            if (!ref.isValid() || ref.getStore() != store) {
                iterator.remove();
            }
        }
    }
}
