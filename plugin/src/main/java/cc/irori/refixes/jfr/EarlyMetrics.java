package cc.irori.refixes.jfr;

import cc.irori.refixes.early.EarlyOptions;
import cc.irori.refixes.early.util.IncidentCounters;
import cc.irori.refixes.early.util.PatchCounters;
import cc.irori.refixes.early.util.PublishedCounters;
import cc.irori.refixes.util.Early;
import java.util.concurrent.atomic.LongAdder;

final class EarlyMetrics {

    private EarlyMetrics() {}

    static boolean active() {
        return Early.isEnabled() && EarlyOptions.hasMixinSelection() && EarlyOptions.enabledMixinCount() > 0;
    }

    static void emit() {
        if (!active()) {
            return;
        }
        emit(
                "Netty queue limit disconnects",
                "MixinNettyChannelConnection",
                IncidentCounters.NETTY_QUEUE_LIMIT_DISCONNECTS);
        emit(
                "Quiche queue limit disconnects",
                "MixinQuicheConnection",
                IncidentCounters.QUICHE_QUEUE_LIMIT_DISCONNECTS);
        emit("Corrupt sections quarantined", "MixinBlockSectionSafety", IncidentCounters.SECTIONS_QUARANTINED);
        emit("Section quarantine failures", "MixinBlockSectionSafety", IncidentCounters.QUARANTINE_FAILURES);
        if (!PatchCounters.ENABLED) {
            return;
        }
        for (PatchCounters.Snapshot counter : PatchCounters.snapshot()) {
            if (counter.enabled()) {
                emit("Patch " + counter.key() + " reached", counter.reached());
                emit("Patch " + counter.key() + " effect", counter.effect());
            }
        }
    }

    private static void emit(String name, String mixin, LongAdder counter) {
        if (EarlyOptions.isMixinEnabled(mixin)) {
            emit(name, counter.sum());
        }
    }

    private static void emit(String name, long total) {
        Recorder.counter(name, PublishedCounters.delta(name, total));
    }
}
