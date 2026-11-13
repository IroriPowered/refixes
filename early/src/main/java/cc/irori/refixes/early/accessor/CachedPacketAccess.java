package cc.irori.refixes.early.accessor;

import io.netty.buffer.ByteBuf;

public interface CachedPacketAccess {

    ByteBuf refixes$getCachedBytes();
}
