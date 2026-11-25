package cc.irori.refixes.early.util;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

@IfHytale(">=0.8.0-pre.1 || >=0.9.0-0")
public final class NPCActivationRecoveryV08 {

    private NPCActivationRecoveryV08() {}

    public static void capture(Holder<EntityStore> holder) {
        NPCActivationRecovery.capture(holder, saved -> {
            TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
            TransformComponent savedTransform = saved.getComponent(TransformComponent.getComponentType());
            if (transform != null && savedTransform != null) {
                savedTransform.setStorageState(transform.getStorageState());
            }
        });
    }
}
