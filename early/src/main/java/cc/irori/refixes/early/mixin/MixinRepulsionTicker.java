package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.modules.entity.repulsion.RepulsionSystems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Pools the ObjectArrayList allocated per entity per tick to reduce GC pressure
@Mixin(RepulsionSystems.RepulsionTicker.class)
public class MixinRepulsionTicker {

    @Unique
    private static final ThreadLocal<ObjectArrayList> refixes$resultList =
            ThreadLocal.withInitial(ObjectArrayList::new);

    @Redirect(method = "tick", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/ObjectArrayList"))
    private ObjectArrayList refixes$poolResults() {
        ObjectArrayList list = refixes$resultList.get();
        list.clear();
        return list;
    }
}
