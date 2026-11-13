package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.QuicConnectionAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "io.netty.handler.codec.quic.QuicheQuicConnection")
public interface MixinQuicConnectionAccess extends QuicConnectionAccess {

    @Override
    @Invoker("isClosed")
    boolean refixes$isClosed();

    @Override
    @Invoker("free")
    void refixes$free();
}
