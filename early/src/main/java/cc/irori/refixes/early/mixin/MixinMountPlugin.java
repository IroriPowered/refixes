package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.Logs;
import com.hypixel.hytale.builtin.mounts.MountPlugin;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MountPlugin.class)
public class MixinMountPlugin {

    @Unique
    private static final HytaleLogger refixes$LOGGER = Logs.logger();

    @Redirect(
            method = "resetOriginalPlayerMovementSettings",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/component/ComponentAccessor;getComponent(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/component/ComponentType;)Lcom/hypixel/hytale/component/Component;"))
    private static <T extends Component<EntityStore>> T refixes$safelyGetComponent(
            ComponentAccessor<EntityStore> instance,
            Ref<EntityStore> ref,
            ComponentType<EntityStore, T> componentType) {
        try {
            return instance.getComponent(ref, componentType);
        } catch (IllegalStateException e) {
            refixes$LOGGER.atWarning().withCause(e).log(
                    "MountPlugin#resetOriginalPlayerMovementSettings(): skipping movement reset for out-of-store mount rider reference");
            return null;
        }
    }
}
