package cc.irori.refixes.early.util;

import java.util.concurrent.atomic.LongAdder;

public final class IncidentCounters {

    public static final LongAdder NETTY_QUEUE_LIMIT_DISCONNECTS = new LongAdder();
    public static final LongAdder QUICHE_QUEUE_LIMIT_DISCONNECTS = new LongAdder();
    public static final LongAdder SECTIONS_QUARANTINED = new LongAdder();
    public static final LongAdder QUARANTINE_FAILURES = new LongAdder();

    private IncidentCounters() {}
}
