package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import cc.irori.refixes.early.util.ThreadJoinBlocker;
import com.hypixel.hytale.server.core.util.thread.TickingThread;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@IfHytale("<0.8.0-pre.1")
@Mixin(TickingThread.class)
public class MixinTickingThreadStop {

    @Redirect(
            method = "stop",
            at = @At(value = "INVOKE", target = "Ljava/lang/Thread;join(J)V"),
            require = 1,
            allow = 1)
    private void refixes$managedJoin(Thread thread, long millis) throws InterruptedException {
        ThreadJoinBlocker.join(thread, millis);
    }
}
