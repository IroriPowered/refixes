package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.TickingThreadRunAccess;
import java.util.concurrent.CompletableFuture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.hypixel.hytale.server.core.util.thread.TickingThread$Run")
public interface MixinTickingThreadRunAccess extends TickingThreadRunAccess {
    @Override
    @Accessor("thread")
    Thread refixes$getThread();

    @Override
    @Accessor("startedFuture")
    CompletableFuture<Void> refixes$getStartedFuture();
}
