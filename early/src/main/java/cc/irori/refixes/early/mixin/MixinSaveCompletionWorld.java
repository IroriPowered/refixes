package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.SaveCompletionOwner;
import cc.irori.refixes.early.util.SaveCompletions;
import com.hypixel.hytale.server.core.universe.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(World.class)
public abstract class MixinSaveCompletionWorld implements SaveCompletionOwner {

    @Unique
    private final SaveCompletions refixes$saveCompletions = new SaveCompletions((World) (Object) this);

    @Override
    public SaveCompletions refixes$saveCompletions() {
        return refixes$saveCompletions;
    }
}
