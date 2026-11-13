package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.QuicConnectionAccess;
import cc.irori.refixes.early.accessor.QuicTimeoutAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import java.net.InetSocketAddress;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "io.netty.handler.codec.quic.QuicheQuicChannel")
public abstract class MixinQuicChannelClose {

    @Unique
    private static final long CLOSE_DEADLINE_MILLIS = 2000L;

    @Shadow
    private InetSocketAddress local;

    @Shadow
    private InetSocketAddress remote;

    @Unique
    private boolean refixes$closeAccepted;

    @Unique
    private boolean refixes$closeSent;

    @Unique
    private Object refixes$retainedConnection;

    @Unique
    private Consumer<Object> refixes$retainedFreeTask;

    @Unique
    private Object refixes$retainedTimeout;

    @Unique
    private java.util.concurrent.Future<?> refixes$closeDeadline;

    @WrapOperation(
            method = "doClose",
            at = @At(value = "INVOKE", target = "Lio/netty/handler/codec/quic/Quiche;quiche_conn_close(JZJJI)I"),
            require = 1,
            allow = 1)
    private int refixes$trackClose(
            long connection, boolean application, long error, long reason, int length, Operation<Integer> original) {
        int result = original.call(connection, application, error, reason, length);
        refixes$closeAccepted = application && result == 0;
        refixes$closeSent = false;
        return result;
    }

    @WrapOperation(
            method = {"writePacket", "connectionSendSegments"},
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lio/netty/channel/Channel;write(Ljava/lang/Object;)Lio/netty/channel/ChannelFuture;"),
            require = 4,
            allow = 4)
    private ChannelFuture refixes$trackCloseWrite(Channel parent, Object message, Operation<ChannelFuture> original) {
        if (refixes$closeAccepted) refixes$closeSent = true;
        return original.call(parent, message);
    }

    @Redirect(
            method = "doClose",
            at = @At(value = "INVOKE", target = "Lio/netty/handler/codec/quic/QuicheQuicConnection;free()V"),
            require = 4,
            allow = 4)
    private void refixes$retainUnsentClose(@Coerce Object connection) {
        QuicConnectionAccess access = (QuicConnectionAccess) connection;
        boolean retain = refixes$closeAccepted && !refixes$closeSent && !access.refixes$isClosed();
        refixes$closeAccepted = false;
        refixes$closeSent = false;
        if (!retain) {
            access.refixes$free();
            return;
        }
        try {
            refixes$closeDeadline = ((Channel) (Object) this)
                    .eventLoop()
                    .schedule(this::refixes$releaseRetained, CLOSE_DEADLINE_MILLIS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException rejected) {
            access.refixes$free();
            return;
        }
        refixes$retainedConnection = connection;
    }

    @SuppressWarnings("unchecked")
    @Redirect(
            method = "doClose",
            at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"),
            require = 4,
            allow = 4)
    private void refixes$deferFreeTask(Consumer<Object> task, Object channel) {
        if (refixes$retainedConnection != null) {
            refixes$retainedFreeTask = task;
            return;
        }
        task.accept(channel);
    }

    @Redirect(
            method = "doClose",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lio/netty/handler/codec/quic/QuicheQuicChannel$TimeoutHandler;cancel()V"),
            require = 4,
            allow = 4)
    private void refixes$keepCloseTimer(@Coerce Object timeout) {
        if (refixes$retainedConnection != null) {
            refixes$retainedTimeout = timeout;
            return;
        }
        ((QuicTimeoutAccess) timeout).refixes$cancel();
    }

    @Redirect(
            method = "doClose",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lio/netty/handler/codec/quic/QuicheQuicChannel;local:Ljava/net/InetSocketAddress;",
                            opcode = Opcodes.PUTFIELD),
            require = 4,
            allow = 4)
    private void refixes$keepLocalWhileRetained(@Coerce Object channel, InetSocketAddress value) {
        if (refixes$retainedConnection == null) local = value;
    }

    @Redirect(
            method = "doClose",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lio/netty/handler/codec/quic/QuicheQuicChannel;remote:Ljava/net/InetSocketAddress;",
                            opcode = Opcodes.PUTFIELD),
            require = 4,
            allow = 4)
    private void refixes$keepRemoteWhileRetained(@Coerce Object channel, InetSocketAddress value) {
        if (refixes$retainedConnection == null) remote = value;
    }

    @Inject(method = "freeIfClosed", at = @At("HEAD"), cancellable = true, require = 1, allow = 1)
    private void refixes$releaseWhenClosed(CallbackInfoReturnable<Boolean> cir) {
        Object connection = refixes$retainedConnection;
        if (connection == null) return;
        if (((QuicConnectionAccess) connection).refixes$isClosed()) {
            refixes$releaseRetained();
            cir.setReturnValue(true);
        } else {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "forceClose", at = @At("HEAD"), require = 1, allow = 1)
    private void refixes$releaseOnForceClose(CallbackInfo ci) {
        refixes$releaseRetained();
    }

    @Unique
    private void refixes$releaseRetained() {
        Object connection = refixes$retainedConnection;
        if (connection == null) return;
        refixes$retainedConnection = null;
        java.util.concurrent.Future<?> deadline = refixes$closeDeadline;
        refixes$closeDeadline = null;
        if (deadline != null) deadline.cancel(false);
        Consumer<Object> task = refixes$retainedFreeTask;
        refixes$retainedFreeTask = null;
        Object timeout = refixes$retainedTimeout;
        refixes$retainedTimeout = null;
        ((QuicConnectionAccess) connection).refixes$free();
        if (task != null) task.accept(this);
        if (timeout != null) ((QuicTimeoutAccess) timeout).refixes$cancel();
        local = null;
        remote = null;
    }
}
