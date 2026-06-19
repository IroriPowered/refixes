package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.net.BackpressureChannelHandler;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.netty.NettyUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.WriteBufferWaterMark;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NettyUtil.class)
public class MixinNettyUtilBackpressure {

    @Inject(
            method = "setChannelHandler(Lio/netty/channel/Channel;Lcom/hypixel/hytale/server/core/io/PacketHandler;)V",
            at = @At("HEAD"))
    private static void refixes$installBackpressureGuard(
            Channel channel, PacketHandler packetHandler, CallbackInfo ci) {
        try {
            ChannelPipeline pipeline = channel.pipeline();
            if (pipeline.get("refixes-backpressure") != null) {
                return;
            }
            int high = Math.max(1 << 20, EarlyOptions.BACKPRESSURE_MAX_OUTBOUND_BYTES.get());
            channel.config().setWriteBufferWaterMark(new WriteBufferWaterMark(high / 2, high));
            long graceMillis = Math.max(1000L, EarlyOptions.BACKPRESSURE_GRACE_MS.get());
            pipeline.addLast("refixes-backpressure", new BackpressureChannelHandler(graceMillis));
        } catch (Throwable ignored) {
        }
    }
}
