package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.WindowAccess;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Window.class)
public interface MixinWindowAccess extends WindowAccess {
    @Override
    @Invoker("onClose")
    void refixes$onClose(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor);
}
