package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.TickingThreadAccess;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TickingThread.class)
public interface MixinTickingThreadAccess extends TickingThreadAccess {

    @Override
    @Accessor("thread")
    Thread refixes$getThread();
}
