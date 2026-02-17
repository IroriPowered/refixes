package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.portals.integrations.PortalRemovalCondition;
import com.hypixel.hytale.builtin.portals.resources.PortalWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PortalWorld.class)
public interface MixinPortalWorldAccessor {

    @Accessor
    PortalRemovalCondition getWorldRemovalCondition();
}
