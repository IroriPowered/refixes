package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.StateSupport;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fixes "java.lang.IllegalStateException: Incorrect store for entity reference"
// in StateSupport#update() when the store for entity ref is incorrect

@Mixin(StateSupport.class)
public abstract class MixinStateSupport {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @Shadow
    public abstract void update(@NonNullDecl ComponentAccessor<EntityStore> componentAccessor);

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void refixes$wrapUpdate(ComponentAccessor<EntityStore> componentAccessor, CallbackInfo ci) {
        if (refixes$WRAPPING.get()) {
            // Run the original method
            return;
        }

        ci.cancel();
        refixes$WRAPPING.set(true);
        try {
            update(componentAccessor);
        } catch (IllegalStateException e) {
            refixes$LOGGER.atWarning().withCause(e).log("StateSupport#update(): Failed to update, discarding");
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
