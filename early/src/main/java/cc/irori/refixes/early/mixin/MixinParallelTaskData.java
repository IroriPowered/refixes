package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.ParallelFailure;
import com.hypixel.hytale.component.data.ForEachTaskData;
import com.hypixel.hytale.component.system.data.EntityDataSystem;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin({ForEachTaskData.class, EntityDataSystem.SystemTaskData.class, EntityTickingSystem.SystemTaskData.class})
public abstract class MixinParallelTaskData implements ParallelFailure.Discardable {
    @Shadow(remap = false)
    public abstract void clear();
}
