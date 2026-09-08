package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.server.core.util.thread.TickingThread;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(TickingThread.class)
public class MixinTickingThread {

    @Shadow
    private Thread thread;

    @Overwrite
    public boolean isInThread() {
        return Thread.currentThread() == this.thread;
    }
}
