package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.util.NettyQueueBudget;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.netty.channel.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "io.netty.handler.codec.quic.QuicheQuicChannel")
public abstract class MixinQuicStreamProgress {

    @ModifyReturnValue(method = "streamSend(JLio/netty/buffer/ByteBuf;Z)I", at = @At("RETURN"), require = 1)
    private int refixes$countSent(int sent) {
        if (sent > 0) {
            NettyQueueBudget budget =
                    ((Channel) (Object) this).attr(NettyQueueBudget.ATTRIBUTE).get();
            if (budget != null) budget.sent(sent);
        }
        return sent;
    }
}
