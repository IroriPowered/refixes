package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.QuicTimeoutAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "io.netty.handler.codec.quic.QuicheQuicChannel$TimeoutHandler")
public interface MixinQuicTimeoutAccess extends QuicTimeoutAccess {

    @Override
    @Invoker("cancel")
    void refixes$cancel();
}
