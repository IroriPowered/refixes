package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.RepulsionPolicy;
import com.hypixel.hytale.server.core.modules.entity.repulsion.RepulsionSystems;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Pools the ReferenceArrayList allocated per entity per tick to reduce GC pressure
@Mixin(RepulsionSystems.RepulsionTicker.class)
public class MixinRepulsionTicker {
    @WrapMethod(method = "tick")
    private void refixes$releaseResults(
            float dt,
            int index,
            com.hypixel.hytale.component.ArchetypeChunk<
                            com.hypixel.hytale.server.core.universe.world.storage.EntityStore>
                    chunk,
            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store,
            com.hypixel.hytale.component.CommandBuffer<
                            com.hypixel.hytale.server.core.universe.world.storage.EntityStore>
                    commandBuffer,
            Operation<Void> original) {
        ReferenceArrayList<Object> previous = RepulsionPolicy.beginTick();
        try {
            original.call(dt, index, chunk, store, commandBuffer);
        } finally {
            RepulsionPolicy.endTick(previous);
        }
    }

    @Redirect(method = "tick", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/ReferenceArrayList"))
    private ReferenceArrayList refixes$poolResults() {
        ReferenceArrayList<Object> list = RepulsionPolicy.borrow();
        RepulsionPolicy.activate(list);
        return list;
    }
}
