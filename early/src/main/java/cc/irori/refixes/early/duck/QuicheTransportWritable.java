package cc.irori.refixes.early.duck;

import java.lang.foreign.MemorySegment;

public interface QuicheTransportWritable {

    boolean refixes$isTransportWritable();

    boolean refixes$probeAfterRecv(MemorySegment connection);
}
