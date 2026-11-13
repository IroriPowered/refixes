package cc.irori.refixes.early.duck;

import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.server.core.io.PacketHandler;
import java.util.concurrent.atomic.AtomicLong;

public interface ChunkTrackerSectionFence {

    AtomicLong SEQUENCE = new AtomicLong();

    ThreadLocal<Write> PENDING = new ThreadLocal<>();

    record Write(
            ChunkTrackerSectionFence tracker,
            PacketHandler handler,
            ToClientPacket packet,
            long ticket,
            int x,
            int y,
            int z,
            boolean snapshot,
            boolean fluid) {}

    void refixes$writeSnapshot(
            long ticket, PacketHandler handler, ToClientPacket packet, int x, int y, int z, boolean fluid);

    boolean refixes$markDirtyIfStillLoading(int x, int y, int z);

    void refixes$writeDelta(PacketHandler handler, ToClientPacket packet, int x, int y, int z, boolean fluid);

    void refixes$send(Write write, Runnable send);
}
