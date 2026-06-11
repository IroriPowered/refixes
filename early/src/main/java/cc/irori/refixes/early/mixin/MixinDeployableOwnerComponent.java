package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.builtin.deployables.component.DeployableComponent;
import com.hypixel.hytale.builtin.deployables.component.DeployableOwnerComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeployableOwnerComponent.class)
public abstract class MixinDeployableOwnerComponent {

    @Shadow
    @Final
    private List<Pair<String, Ref<EntityStore>>> deployables;

    @Shadow
    @Final
    private List<Ref<EntityStore>> deployablesForDestruction;

    @Shadow
    public abstract void deRegisterDeployable(String id, Ref<EntityStore> deployable);

    @Unique
    private static boolean refixes$isStale(Ref<EntityStore> ref, Store<EntityStore> store) {
        return ref == null || !ref.isValid() || ref.getStore() != store;
    }

    @Inject(method = "registerDeployable", at = @At("HEAD"))
    private void refixes$pruneStaleDeployables(
            Ref<EntityStore> owner,
            DeployableComponent deployableComp,
            String id,
            Ref<EntityStore> deployable,
            Store<EntityStore> store,
            CallbackInfo ci) {
        List<Pair<String, Ref<EntityStore>>> stale = null;
        for (Pair<String, Ref<EntityStore>> pair : deployables) {
            if (refixes$isStale(pair.value(), store)) {
                if (stale == null) {
                    stale = new ObjectArrayList<>();
                }
                stale.add(pair);
            }
        }
        if (stale != null) {
            for (Pair<String, Ref<EntityStore>> pair : stale) {
                deRegisterDeployable(pair.key(), pair.value());
            }
        }
    }

    @Inject(method = "handleOverMaxDeployableDestruction", at = @At("HEAD"))
    private void refixes$pruneStaleDestructionRefs(CommandBuffer<EntityStore> commandBuffer, CallbackInfo ci) {
        Store<EntityStore> store = commandBuffer.getStore();
        deployablesForDestruction.removeIf(ref -> refixes$isStale(ref, store));
    }
}
