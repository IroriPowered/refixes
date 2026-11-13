package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.MotionControllerAccess;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.movement.controllers.MotionControllerBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MotionControllerBase.class)
public interface MixinMotionControllerAccess extends MotionControllerAccess {

    @Override
    @Invoker("readEntityPosition")
    void refixes$readEntityPosition(Ref<EntityStore> ref, ComponentAccessor<EntityStore> componentAccessor);
}
