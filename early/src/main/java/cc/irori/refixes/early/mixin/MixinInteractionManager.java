package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InteractionManager.class)
public abstract class MixinInteractionManager {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Unique
    private static final ThreadLocal<Boolean> refixes$WRAPPING = ThreadLocal.withInitial(() -> false);

    @Shadow
    @Nullable
    protected abstract InteractionSyncData serverTick(
            @NonNullDecl Ref<EntityStore> ref, @NonNullDecl InteractionChain chain, long tickTime);

    @Shadow
    public abstract void cancelChains(@NonNullDecl InteractionChain chain);

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void refixes$wrapServerTick(
            Ref<EntityStore> ref,
            InteractionChain chain,
            long tickTime,
            CallbackInfoReturnable<InteractionSyncData> cir) {
        if (refixes$WRAPPING.get()) {
            // Run the original method
            return;
        }

        cir.cancel();
        refixes$WRAPPING.set(true);
        try {
            cir.setReturnValue(serverTick(ref, chain, tickTime));
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Client took too long")) {
                refixes$LOGGER.atWarning().withCause(e).log("InteractionManager#serverTick(): Interaction timed out");
                cancelChains(chain);
                cir.setReturnValue(null);
                return;
            }
            throw e;
        } finally {
            refixes$WRAPPING.set(false);
        }
    }

    @ModifyArg(
            method = {"tickChain", "removeInteractionIfFinished"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/logger/HytaleLogger;at(Ljava/util/logging/Level;)Lcom/hypixel/hytale/logger/HytaleLogger$Api;"))
    private Level refixes$redirectLogLevel(Level level) {
        if (level == Level.SEVERE) {
            return Level.FINE;
        }
        return level;
    }
}
