package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.duck.ChunkTrackerSectionFence;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.io.ChannelConnection;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PacketHandler.class)
public abstract class MixinPacketHandlerSectionFence {

    @Inject(method = "writePacket", at = @At("HEAD"))
    private void refixes$claimFence(
            ToClientPacket packet,
            boolean cache,
            CallbackInfoReturnable<Boolean> cir,
            @Share("refixes$fence") LocalRef<ChunkTrackerSectionFence.Write> fence) {
        ChunkTrackerSectionFence.Write write = ChunkTrackerSectionFence.PENDING.get();
        if (write == null) return;
        ChunkTrackerSectionFence.PENDING.remove();
        if (write.packet() == packet && write.handler() == (Object) this) fence.set(write);
    }

    @WrapOperation(
            method = "writePacket",
            at = {
                @At(
                        value = "INVOKE",
                        target =
                                "Lcom/hypixel/hytale/protocol/io/ChannelConnection;write(Lcom/hypixel/hytale/protocol/ToClientPacket;)V"),
                @At(
                        value = "INVOKE",
                        target =
                                "Lcom/hypixel/hytale/protocol/io/ChannelConnection;writeAndFlush(Lcom/hypixel/hytale/protocol/ToClientPacket;)V")
            },
            require = 2,
            allow = 2)
    private void refixes$fencedSend(
            ChannelConnection channel,
            ToClientPacket packet,
            Operation<Void> original,
            @Share("refixes$fence") LocalRef<ChunkTrackerSectionFence.Write> fence) {
        ChunkTrackerSectionFence.Write write = fence.get();
        if (write == null) {
            original.call(channel, packet);
            return;
        }
        fence.set(null);
        write.tracker().refixes$send(write, () -> original.call(channel, packet));
    }
}
