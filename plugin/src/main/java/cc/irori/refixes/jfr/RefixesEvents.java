package cc.irori.refixes.jfr;

import cc.irori.refixes.config.impl.DiagnosticsConfig;
import cc.irori.refixes.util.Logs;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.DoubleSupplier;
import java.util.regex.Pattern;

public final class RefixesEvents {

    private static final HytaleLogger LOGGER = Logs.logger();
    private static final boolean AVAILABLE =
            ModuleLayer.boot().findModule("jdk.jfr").isPresent();
    private static final Pattern UUID =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private static final Map<String, LongAdder> counters = new ConcurrentHashMap<>();
    private static final List<Gauge> gauges = new CopyOnWriteArrayList<>();
    private static final Set<String> failedMetrics = ConcurrentHashMap.newKeySet();
    private static final Object emitLock = new Object();
    private static volatile boolean recording;

    private RefixesEvents() {}

    public static LongAdder counter(String name) {
        return counters.computeIfAbsent(name, key -> new LongAdder());
    }

    public static AutoCloseable gauge(String name, DoubleSupplier supplier) {
        Gauge gauge = new Gauge(name, supplier);
        gauges.add(gauge);
        return gauge;
    }

    public static void action(String category, String message) {
        if (recording) {
            Recorder.action(category, message);
        }
    }

    public static synchronized void start(String refixesVersion) {
        if (!DiagnosticsConfig.get().getValue(DiagnosticsConfig.JFR_EVENTS)) {
            return;
        }
        if (!AVAILABLE) {
            LOGGER.atInfo().log(
                    "This Java runtime has no JDK Flight Recorder module (jdk.jfr), so Refixes records no JFR events");
            return;
        }
        Recorder.start(refixesVersion);
        recording = true;
    }

    public static synchronized void stop() {
        if (recording) {
            recording = false;
            Recorder.stop();
        }
    }

    static String redact(String message) {
        return UUID.matcher(message).replaceAll("<uuid>");
    }

    static void emitMetrics() {
        synchronized (emitLock) {
            for (Map.Entry<String, LongAdder> counter : counters.entrySet()) {
                Recorder.counter(counter.getKey(), counter.getValue().sumThenReset());
            }
            for (Gauge gauge : gauges) {
                double value;
                try {
                    value = gauge.supplier.getAsDouble();
                } catch (Throwable t) {
                    if (failedMetrics.add(gauge.name)) {
                        LOGGER.atWarning().withCause(t).log(
                                "Gauge %s failed, its refixes.Metric events are skipped while it fails", gauge.name);
                    }
                    continue;
                }
                Recorder.gauge(gauge.name, value);
            }
            try {
                EarlyMetrics.emit();
            } catch (Throwable t) {
                if (failedMetrics.add("early")) {
                    LOGGER.atWarning().withCause(t).log(
                            "Early patch counters failed, their refixes.Metric events are skipped while they fail");
                }
            }
        }
    }

    private static final class Gauge implements AutoCloseable {
        private final String name;
        private final DoubleSupplier supplier;

        private Gauge(String name, DoubleSupplier supplier) {
            this.name = name;
            this.supplier = supplier;
        }

        @Override
        public void close() {
            gauges.remove(this);
        }
    }
}
