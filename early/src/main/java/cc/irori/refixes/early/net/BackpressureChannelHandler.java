package cc.irori.refixes.early.net;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BackpressureChannelHandler extends ChannelDuplexHandler {

    private final long graceMillis;
    private ScheduledFuture<?> pendingClose;

    public BackpressureChannelHandler(long graceMillis) {
        this.graceMillis = graceMillis;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        evaluate(ctx);
        super.handlerAdded(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        evaluate(ctx);
        super.channelWritabilityChanged(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        cancel();
        super.channelInactive(ctx);
    }

    private void evaluate(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        if (channel.isWritable()) {
            cancel();
        } else if (pendingClose == null) {
            pendingClose =
                    ctx.executor().schedule(() -> closeIfStillStalled(channel), graceMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void closeIfStillStalled(Channel channel) {
        pendingClose = null;
        if (channel.isActive() && !channel.isWritable()) {
            Channel target = channel.parent() != null ? channel.parent() : channel;
            target.close();
        }
    }

    private void cancel() {
        if (pendingClose != null) {
            pendingClose.cancel(false);
            pendingClose = null;
        }
    }
}
