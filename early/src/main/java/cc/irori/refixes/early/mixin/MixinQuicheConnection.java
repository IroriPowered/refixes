package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.QuicheQueueOwner;
import cc.irori.refixes.early.duck.QuicheTransportWritable;
import cc.irori.refixes.early.util.IncidentCounters;
import cc.irori.refixes.early.util.QuicheQueueBudget;
import com.hypixel.hytale.lib.quiche.QuicheChannel;
import com.hypixel.hytale.lib.quiche.QuicheNative;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.protocol.packets.connection.QuicApplicationErrorCode;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.hypixel.hytale.lib.quiche.QuicheConnection")
public abstract class MixinQuicheConnection implements QuicheQueueOwner {

    @Unique
    private final QuicheQueueBudget refixes$queueBudget = new QuicheQueueBudget();

    @Unique
    private boolean refixes$queueCloseRequested;

    @Shadow
    @Final
    private static HytaleLogger LOGGER;

    @Shadow
    @Final
    private String dcidLabel;

    @Shadow
    @Final
    public MemorySegment connHandle;

    @Shadow
    @Final
    private Long2ObjectOpenHashMap<QuicheChannel> streams;

    @Unique
    private boolean refixes$recvApplied;

    @Override
    public QuicheQueueBudget refixes$queueBudget() {
        return refixes$queueBudget;
    }

    @ModifyExpressionValue(
            method = "process",
            at =
                    @At(
                            value = "FIELD",
                            target = "Lcom/hypixel/hytale/lib/quiche/QuicheConnection;stopRequested:Z",
                            opcode = Opcodes.GETFIELD),
            require = 1,
            allow = 1)
    private boolean refixes$closeOnQueueOverflow(boolean stopping) {
        if (!refixes$queueBudget.overflowed() || refixes$queueCloseRequested) return stopping;
        refixes$queueCloseRequested = true;
        if (!QuicheNative.connIsClosed(connHandle) && !QuicheNative.connIsDraining(connHandle)) {
            IncidentCounters.QUICHE_QUEUE_LIMIT_DISCONNECTS.increment();
            LOGGER.atWarning().log("Outgoing packet queue limit exceeded for %s", dcidLabel);
            FormattedMessage message = new FormattedMessage();
            message.rawText = "Outgoing packet queue limit exceeded";
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment reason = arena.allocate(message.computeSize());
                message.serialize(reason, 0);
                QuicheNative.connClose(
                        connHandle, true, QuicApplicationErrorCode.NoError.getValue(), reason, reason.byteSize());
            }
        }
        return stopping;
    }

    @WrapOperation(
            method = "process",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/lib/quiche/QuicheNative;connRecv(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;JLjava/lang/foreign/MemorySegment;)J"),
            require = 1,
            allow = 1)
    private long refixes$observeRecv(
            MemorySegment connection, MemorySegment data, long length, MemorySegment info, Operation<Long> original) {
        long received = original.call(connection, data, length, info);
        if (received >= 0) refixes$recvApplied = true;
        return received;
    }

    @Inject(
            method = "process",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/lib/quiche/QuicheConnection;applyPathEvents(Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;Ljava/lang/foreign/MemorySegment;)V"),
            require = 1,
            allow = 1)
    private void refixes$probeStoppedStreams(CallbackInfo ci) {
        if (!refixes$recvApplied) return;
        refixes$recvApplied = false;
        if (QuicheNative.connIsClosed(connHandle) || QuicheNative.connIsDraining(connHandle)) return;
        for (QuicheChannel channel : streams.values()) {
            if (((QuicheTransportWritable) (Object) channel).refixes$probeAfterRecv(connHandle)) return;
        }
    }

    @WrapOperation(
            method = "process",
            at = @At(value = "INVOKE", target = "Lcom/hypixel/hytale/lib/quiche/QuicheChannel;isWritable()Z"),
            require = 1,
            allow = 1)
    private boolean refixes$keepTransportWritability(QuicheChannel channel, Operation<Boolean> original) {
        return ((QuicheTransportWritable) (Object) channel).refixes$isTransportWritable();
    }
}
