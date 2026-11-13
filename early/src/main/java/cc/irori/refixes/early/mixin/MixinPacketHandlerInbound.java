package cc.irori.refixes.early.mixin;

import com.hypixel.hytale.protocol.ToServerPacket;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PacketHandler.class)
public abstract class MixinPacketHandlerInbound {

    @Inject(
            method = "handle(Lcom/hypixel/hytale/protocol/ToServerPacket;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            allow = 1)
    private void refixes$filterInbound(ToServerPacket packet, CallbackInfo ci) {
        if (PacketAdapters.__handleInbound((PacketHandler) (Object) this, packet)) ci.cancel();
    }
}
