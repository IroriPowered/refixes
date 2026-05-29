package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Set;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fixes "Entity is not visible!" crash in EntityViewer.queueUpdate/queueRemove.

@Mixin(EntityTrackerSystems.EntityViewer.class)
public abstract class MixinEntityViewer {

    @Shadow
    public Set<Ref<EntityStore>> visible;

    @Inject(method = "queueUpdate", at = @At("HEAD"), cancellable = true)
    private void refixes$fixInvisibleUpdate(
            @Nonnull Ref<EntityStore> ref, @Nonnull ComponentUpdate update, CallbackInfo ci) {
        if (!this.visible.contains(ref)) {
            ci.cancel();
        }
    }

    @Inject(method = "queueRemove", at = @At("HEAD"), cancellable = true)
    private void refixes$fixInvisibleRemove(
            @Nonnull Ref<EntityStore> ref, @Nonnull ComponentUpdateType type, CallbackInfo ci) {
        if (!this.visible.contains(ref)) {
            ci.cancel();
        }
    }
}
