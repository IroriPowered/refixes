package cc.irori.refixes.early.accessor;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public interface MotionControllerAccess {

    void refixes$readEntityPosition(Ref<EntityStore> ref, ComponentAccessor<EntityStore> componentAccessor);
}
