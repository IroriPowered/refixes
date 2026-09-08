package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.ParallelAnimationDispatch;
import com.hypixel.hytale.builtin.deployables.DeployablesUtils;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.function.consumer.TriConsumer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DeployablesUtils.class)
public abstract class MixinDeployablesUtils {

    @Redirect(
            method = {
                "playAnimation(Lcom/hypixel/hytale/component/Store;ILcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/builtin/deployables/config/DeployableConfig;Lcom/hypixel/hytale/protocol/AnimationSlot;Ljava/lang/String;Ljava/lang/String;)V",
                "stopAnimation(Lcom/hypixel/hytale/component/Store;ILcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/protocol/AnimationSlot;)V"
            },
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/universe/world/PlayerUtil;forEachPlayerThatCanSeeEntity(Lcom/hypixel/hytale/component/Ref;Lcom/hypixel/hytale/function/consumer/TriConsumer;Lcom/hypixel/hytale/component/ComponentAccessor;)V"))
    private static void refixes$dispatchAnimation(
            Ref<EntityStore> ref,
            TriConsumer<Ref<EntityStore>, PlayerRef, ComponentAccessor<EntityStore>> consumer,
            ComponentAccessor<EntityStore> accessor) {
        ParallelAnimationDispatch.forEachViewer(ref, consumer, null, accessor);
    }
}
