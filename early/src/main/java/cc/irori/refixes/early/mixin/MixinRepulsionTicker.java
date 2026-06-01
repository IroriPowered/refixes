package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.modules.entity.repulsion.RepulsionSystems;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Pools the ReferenceArrayList allocated per entity per tick to reduce GC pressure
@Mixin(RepulsionSystems.RepulsionTicker.class)
public class MixinRepulsionTicker {

    @Unique
    private static final ThreadLocal<ReferenceArrayList> refixes$resultList =
            ThreadLocal.withInitial(ReferenceArrayList::new);

    @Redirect(method = "tick", at = @At(value = "NEW", target = "it/unimi/dsi/fastutil/objects/ReferenceArrayList"))
    private ReferenceArrayList refixes$poolResults() {
        ReferenceArrayList list = refixes$resultList.get();
        list.clear();
        return list;
    }
}
