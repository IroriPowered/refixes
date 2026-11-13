package cc.irori.refixes.early.mixin;

import cc.irori.refixes.early.accessor.CachedPacketAccess;
import com.hypixel.hytale.protocol.CachedPacket;
import io.netty.buffer.ByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CachedPacket.class)
public interface MixinCachedPacketAccess extends CachedPacketAccess {

    @Override
    @Accessor("cachedBytes")
    ByteBuf refixes$getCachedBytes();
}
