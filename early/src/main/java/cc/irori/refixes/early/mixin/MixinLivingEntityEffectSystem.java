package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.modules.entity.livingentity.LivingEntityEffectSystem;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.time.Instant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityEffectSystem.class)
public class MixinLivingEntityEffectSystem {

    @ModifyExpressionValue(
            method = "canApplyEffect",
            at = @At(value = "INVOKE", target = "Ljava/time/Instant;now()Ljava/time/Instant;"),
            require = 1,
            allow = 1)
    private static Instant refixes$worldTime(
            Instant original, Ref<EntityStore> ref, EntityEffect effect, ComponentAccessor<EntityStore> accessor) {
        return accessor.getResource(TimeResource.getResourceType()).getNow();
    }
}
