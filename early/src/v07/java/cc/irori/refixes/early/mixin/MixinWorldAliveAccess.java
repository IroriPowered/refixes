package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.accessor.WorldAliveAccess;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@IfHytale("<0.8.0-pre.1")
@Mixin(World.class)
public interface MixinWorldAliveAccess extends WorldAliveAccess {

    @Override
    @Accessor("alive")
    AtomicBoolean refixes$getAlive();
}
