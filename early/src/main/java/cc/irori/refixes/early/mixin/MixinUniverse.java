package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Universe.class)
public abstract class MixinUniverse {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @Shadow
    protected abstract void lambda$removePlayer$2(PlayerRef par1, Void par2, Throwable par3);

    @Inject(method = "lambda$removePlayer$0", at = @At("HEAD"), cancellable = true)
    private static void refixes$guardAsyncRemoval(Ref ref, CallbackInfo ci) {
        if (!ref.isValid()) {
            ci.cancel();
        }
    }

    @Inject(method = "lambda$removePlayer$2", at = @At("HEAD"), cancellable = true)
    private void refixes$wrapRemovePlayerComplete(PlayerRef playerRef, Void result, Throwable error, CallbackInfo ci) {
        if (refixes$WRAPPING.get()) {
            return;
        }

        ci.cancel();
        refixes$WRAPPING.set(true);
        try {
            lambda$removePlayer$2(playerRef, result, error);
        } catch (IllegalStateException e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "Universe#removePlayer(): Failed to finalize player removal (%s)", playerRef.getUsername());
        } finally {
            refixes$WRAPPING.set(false);
        }
    }
}
