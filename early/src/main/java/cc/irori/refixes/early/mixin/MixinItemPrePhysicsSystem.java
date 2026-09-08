package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.modules.entity.item.ItemPrePhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemPrePhysicsSystem.class)
public abstract class MixinItemPrePhysicsSystem {
    @Inject(method = "isParallel", at = @At("HEAD"), cancellable = true)
    private void refixes$requireOwnerThread(
            int archetypeChunkSize, int taskCount, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
