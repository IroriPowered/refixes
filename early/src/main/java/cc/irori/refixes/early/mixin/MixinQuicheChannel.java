package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.lib.quiche.QuicheChannel;
import com.hypixel.hytale.protocol.NetworkChannel;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.io.ConnectionHandler;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.lang.foreign.Arena;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(QuicheChannel.class)
public abstract class MixinQuicheChannel {

    @Shadow
    @Final
    private ConcurrentLinkedQueue<ToClientPacket> packetQueue;

    @Shadow
    @Final
    private AtomicBoolean closed;

    @Shadow
    @Final
    private Arena arena;

    @Shadow
    private Arena tempPacketArena;

    @Shadow
    private void freeTempSendBuffer() {}

    @WrapMethod(method = "close()V")
    private void refixes$clearQueueOnClose(Operation<Void> original) {
        try {
            original.call();
        } finally {
            if (closed.get()) packetQueue.clear();
        }
    }

    @WrapOperation(
            method = "close()V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/protocol/io/ConnectionHandler;closed(Lcom/hypixel/hytale/protocol/NetworkChannel;)V"))
    private void refixes$freeOnClosedFailure(
            ConnectionHandler handler, NetworkChannel channel, Operation<Void> original) {
        try {
            original.call(handler, channel);
        } catch (Throwable failure) {
            try {
                freeTempSendBuffer();
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            try {
                if (tempPacketArena != null) tempPacketArena.close();
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            try {
                arena.close();
            } catch (Throwable cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Inject(
            method = {
                "write(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "write([Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush([Lcom/hypixel/hytale/protocol/ToClientPacket;)V"
            },
            at = @At("HEAD"),
            cancellable = true)
    private void refixes$dropWriteAfterClose(CallbackInfo ci) {
        if (closed.get()) ci.cancel();
    }

    @Inject(
            method = {
                "write(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush(Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "write([Lcom/hypixel/hytale/protocol/ToClientPacket;)V",
                "writeAndFlush([Lcom/hypixel/hytale/protocol/ToClientPacket;)V"
            },
            at = @At("RETURN"))
    private void refixes$clearRacedWrite(CallbackInfo ci) {
        if (closed.get()) packetQueue.clear();
    }
}
