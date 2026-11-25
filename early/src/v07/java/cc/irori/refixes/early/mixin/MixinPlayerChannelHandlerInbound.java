package cc.irori.refixes.early.mixin;

import cc.irori.hyinit.api.IfHytale;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.netty.PlayerChannelHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@IfHytale("<0.8.0-pre.1")
@Mixin(PlayerChannelHandler.class)
public abstract class MixinPlayerChannelHandlerInbound {

    @Redirect(
            method = "channelRead(Lio/netty/channel/ChannelHandlerContext;Ljava/lang/Object;)V",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lcom/hypixel/hytale/server/core/io/adapter/PacketAdapters;__handleInbound(Lcom/hypixel/hytale/server/core/io/PacketHandler;Lcom/hypixel/hytale/protocol/Packet;)Z"),
            require = 1,
            allow = 1)
    private boolean refixes$filterInSharedHandler(PacketHandler handler, Packet packet) {
        return false;
    }
}
