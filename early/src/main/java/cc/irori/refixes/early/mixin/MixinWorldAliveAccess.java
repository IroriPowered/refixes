package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.WorldAliveAccess;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(World.class)
public interface MixinWorldAliveAccess extends WorldAliveAccess {

    @Override
    @Accessor("alive")
    AtomicBoolean refixes$getAlive();
}
