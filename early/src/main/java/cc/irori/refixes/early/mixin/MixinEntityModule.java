package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityModule.class)
public class MixinEntityModule {

    @ModifyExpressionValue(
            method = "getComponentType",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lcom/hypixel/hytale/server/core/modules/entity/EntityModule;isDisabled()Z"),
            require = 1,
            allow = 1)
    private boolean refixes$retainComponentTypes(boolean disabled) {
        return false;
    }
}
